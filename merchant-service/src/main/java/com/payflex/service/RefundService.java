package com.payflex.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflex.dto.CreateRefundRequest;
import com.payflex.dto.RefundResponse;
import com.payflex.model.MerchantBalance;
import com.payflex.model.PaymentIntent;
import com.payflex.model.Refund;
import com.payflex.repository.MerchantBalanceRepository;
import com.payflex.repository.PaymentIntentRepository;
import com.payflex.repository.RefundRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class RefundService {
    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final MerchantBalanceRepository merchantBalanceRepository;
    private final ObjectMapper objectMapper;

    public RefundService(RefundRepository refundRepository,
                         PaymentIntentRepository paymentIntentRepository,
                         MerchantBalanceRepository merchantBalanceRepository,
                         ObjectMapper objectMapper) {
        this.refundRepository = refundRepository;
        this.paymentIntentRepository = paymentIntentRepository;
        this.merchantBalanceRepository = merchantBalanceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Crear un retiro de dinero (withdrawal) del saldo disponible del merchant
     * Se debe especificar el payment intent del cual provienen los fondos
     */
    public Mono<RefundResponse> createRefund(CreateRefundRequest request) {
        log.info("[createRefund] Creating withdrawal from payment intent: {}", request);

        // Validar que se proporcione el paymentIntentId y el monto
        if (request.getPaymentIntentId() == null) {
            return Mono.error(new IllegalArgumentException("PaymentIntentId is required"));
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return Mono.error(new IllegalArgumentException("Amount is required and must be greater than zero"));
        }

        // Obtener el payment intent para validaciones
        return paymentIntentRepository.findById(request.getPaymentIntentId())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Payment intent not found: " + request.getPaymentIntentId())))
                .flatMap(paymentIntent -> validateWithdrawal(request, paymentIntent)
                        .flatMap(validAmount -> createAndSaveWithdrawal(request, paymentIntent, validAmount))
                )
                .flatMap(this::mapToResponse)
                .doOnSuccess(response -> log.info("[createRefund] Withdrawal created successfully: {}", response.getId()))
                .doOnError(error -> log.error("[createRefund] Error creating withdrawal", error));
    }

    /**
     * Validar el retiro de dinero
     */
    private Mono<BigDecimal> validateWithdrawal(CreateRefundRequest request, PaymentIntent paymentIntent) {
        // Validar que el payment intent esté en estado succeeded
        if (!"succeeded".equals(paymentIntent.getStatus())) {
            return Mono.error(new IllegalArgumentException(
                    "Cannot withdraw from payment intent with status: " + paymentIntent.getStatus()));
        }

        BigDecimal withdrawalAmount = request.getAmount();

        // Verificar que el monto no exceda el monto del payment intent
        if (withdrawalAmount.compareTo(paymentIntent.getAmount()) > 0) {
            return Mono.error(new IllegalArgumentException(
                    String.format("Withdrawal amount %s cannot exceed payment intent amount %s",
                            withdrawalAmount, paymentIntent.getAmount())));
        }

        // Calcular cuánto ya se ha retirado de este payment intent
        return refundRepository.sumRefundedAmountByPaymentIntentId(paymentIntent.getId())
                .defaultIfEmpty(0.0)
                .flatMap(totalWithdrawn -> {
                    BigDecimal totalWithdrawnDecimal = BigDecimal.valueOf(totalWithdrawn);
                    BigDecimal availableFromPaymentIntent = paymentIntent.getAmount().subtract(totalWithdrawnDecimal);

                    if (withdrawalAmount.compareTo(availableFromPaymentIntent) > 0) {
                        return Mono.error(new IllegalArgumentException(
                                String.format("Withdrawal amount %s exceeds available from this payment intent %s (already withdrawn: %s)",
                                        withdrawalAmount, availableFromPaymentIntent, totalWithdrawnDecimal)));
                    }

                    // Verificar el saldo disponible del merchant
                    return merchantBalanceRepository.findByMerchantId(paymentIntent.getMerchantId())
                            .switchIfEmpty(createInitialBalance(paymentIntent.getMerchantId(), paymentIntent.getCurrency()))
                            .flatMap(balance -> {
                                if (withdrawalAmount.compareTo(balance.getAvailableBalance()) > 0) {
                                    return Mono.error(new IllegalArgumentException(
                                            String.format("Insufficient balance. Available: %s, Requested: %s",
                                                    balance.getAvailableBalance(), withdrawalAmount)));
                                }
                                return Mono.just(withdrawalAmount);
                            });
                });
    }

    /**
     * Crear balance inicial si no existe
     */
    private Mono<MerchantBalance> createInitialBalance(String merchantId, String currency) {
        MerchantBalance balance = MerchantBalance.builder()
                .id("bal_" + UUID.randomUUID().toString().replace("-", ""))
                .merchantId(merchantId)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .currency(currency)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isNew(true)
                .build();

        return merchantBalanceRepository.save(balance);
    }

    /**
     * Crear y guardar el withdrawal/refund
     */
    private Mono<Refund> createAndSaveWithdrawal(CreateRefundRequest request, PaymentIntent paymentIntent, BigDecimal amount) {
        LocalDateTime now = LocalDateTime.now();
        String refundId = "wd_" + UUID.randomUUID().toString().replace("-", ""); // wd = withdrawal

        Refund refund = Refund.builder()
                .id(refundId)
                .chargeId(null)
                .paymentIntentId(paymentIntent.getId())
                .merchantId(paymentIntent.getMerchantId())
                .amount(amount)
                .currency(paymentIntent.getCurrency())
                .status("pending")
                .reason(request.getReason() != null ? request.getReason() : "withdrawal")
                .createdAt(now)
                .updatedAt(now)
                .isNew(true)
                .build();

        // Convertir metadata a JSON string si existe
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            try {
                String metadataJson = objectMapper.writeValueAsString(request.getMetadata());
                refund.setMetadataFromString(metadataJson);
            } catch (JsonProcessingException e) {
                log.warn("[createAndSaveWithdrawal] Error converting metadata to JSON", e);
            }
        }

        return refundRepository.save(refund)
                .flatMap(savedRefund -> {
                    savedRefund.markAsNotNew();
                    // Descontar del balance disponible del merchant
                    return merchantBalanceRepository.decrementAvailableBalance(
                            paymentIntent.getMerchantId(),
                            amount.doubleValue()
                    )
                    .flatMap(rowsUpdated -> {
                        if (rowsUpdated == 0) {
                            // Si no se pudo descontar, cancelar el refund
                            savedRefund.setStatus("failed");
                            savedRefund.setUpdatedAt(LocalDateTime.now());
                            return refundRepository.save(savedRefund)
                                    .flatMap(failedRefund -> Mono.error(new IllegalArgumentException(
                                            "Could not decrement balance. Insufficient funds or balance not found.")));
                        }
                        // Actualizar el estado a succeeded
                        savedRefund.setStatus("succeeded");
                        savedRefund.setUpdatedAt(LocalDateTime.now());
                        return refundRepository.save(savedRefund);
                    });
                });
    }

    /**
     * Obtener un refund/withdrawal por ID
     */
    public Mono<RefundResponse> getRefund(String refundId) {
        log.info("[getRefund] Fetching withdrawal: {}", refundId);

        return refundRepository.findById(refundId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Withdrawal not found: " + refundId)))
                .flatMap(this::mapToResponse);
    }

    /**
     * Obtener todos los withdrawals de un payment intent
     */
    public Flux<RefundResponse> getRefundsByPaymentIntent(String paymentIntentId) {
        log.info("[getRefundsByPaymentIntent] Fetching withdrawals for payment intent: {}", paymentIntentId);

        return refundRepository.findByPaymentIntentId(paymentIntentId)
                .flatMap(this::mapToResponse);
    }

    /**
     * Obtener todos los withdrawals de un merchant
     */
    public Flux<RefundResponse> getRefundsByMerchant(String merchantId) {
        log.info("[getRefundsByMerchant] Fetching withdrawals for merchant: {}", merchantId);

        return refundRepository.findByMerchantId(merchantId)
                .flatMap(this::mapToResponse);
    }

    /**
     * Obtener withdrawals de un merchant filtrados por estado
     */
    public Flux<RefundResponse> getRefundsByMerchantAndStatus(String merchantId, String status) {
        log.info("[getRefundsByMerchantAndStatus] Fetching withdrawals for merchant: {} with status: {}", merchantId, status);

        return refundRepository.findByMerchantIdAndStatus(merchantId, status)
                .flatMap(this::mapToResponse);
    }

    /**
     * Cancelar un withdrawal (solo si está en pending)
     */
    public Mono<Void> cancelRefund(String refundId) {
        log.info("[cancelRefund] Canceling withdrawal: {}", refundId);

        return refundRepository.findById(refundId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Withdrawal not found: " + refundId)))
                .flatMap(refund -> {
                    if (!"pending".equals(refund.getStatus())) {
                        return Mono.error(new IllegalArgumentException(
                                "Cannot cancel withdrawal with status: " + refund.getStatus()));
                    }

                    // Devolver el dinero al balance
                    return merchantBalanceRepository.incrementAvailableBalance(
                            refund.getMerchantId(),
                            refund.getAmount().doubleValue()
                    )
                    .then(Mono.defer(() -> {
                        refund.setStatus("canceled");
                        refund.setUpdatedAt(LocalDateTime.now());
                        return refundRepository.save(refund);
                    }));
                })
                .then();
    }

    /**
     * Obtener el balance disponible de un merchant
     */
    public Mono<MerchantBalance> getMerchantBalance(String merchantId) {
        log.info("[getMerchantBalance] Fetching balance for merchant: {}", merchantId);

        return merchantBalanceRepository.findByMerchantId(merchantId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Merchant balance not found: " + merchantId)));
    }

    /**
     * Actualizar el balance disponible cuando un payment intent es successful
     * Este método debería ser llamado desde PaymentIntentService
     */
    public Mono<Void> incrementMerchantBalance(String merchantId, BigDecimal amount, String currency) {
        log.info("[incrementMerchantBalance] Incrementing balance for merchant: {} by {}", merchantId, amount);

        return merchantBalanceRepository.findByMerchantId(merchantId)
                .switchIfEmpty(createInitialBalance(merchantId, currency))
                .flatMap(balance -> merchantBalanceRepository.incrementAvailableBalance(merchantId, amount.doubleValue()))
                .then();
    }

    /**
     * Mapear Refund a RefundResponse
     */
    private Mono<RefundResponse> mapToResponse(Refund refund) {
        RefundResponse response = RefundResponse.builder()
                .id(refund.getId())
                .chargeId(refund.getChargeId())
                .paymentIntentId(refund.getPaymentIntentId())
                .merchantId(refund.getMerchantId())
                .amount(refund.getAmount())
                .currency(refund.getCurrency())
                .status(refund.getStatus())
                .reason(refund.getReason())
                .createdAt(refund.getCreatedAt())
                .updatedAt(refund.getUpdatedAt())
                .build();

        // Convertir metadata de JSON a Map
        if (refund.getMetadata() != null) {
            try {
                String metadataJson = refund.getMetadataAsString();
                @SuppressWarnings("unchecked")
                Map<String, Object> metadataMap = objectMapper.readValue(metadataJson, Map.class);
                response.setMetadata(metadataMap);
            } catch (JsonProcessingException e) {
                log.warn("[mapToResponse] Error parsing metadata JSON", e);
            }
        }

        return Mono.just(response);
    }
}

