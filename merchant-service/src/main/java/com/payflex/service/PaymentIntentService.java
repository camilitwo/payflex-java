package com.payflex.service;

import com.payflex.dto.CreatePaymentIntentRequest;
import com.payflex.dto.PaymentIntentResponse;
import com.payflex.dto.TransactionListResponse;
import com.payflex.dto.UpdatePaymentIntentRequest;
import com.payflex.model.PaymentIntent;
import com.payflex.repository.PaymentIntentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentIntentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentIntentService.class);

    private final PaymentIntentRepository paymentIntentRepository;

    public PaymentIntentService(PaymentIntentRepository paymentIntentRepository) {
        this.paymentIntentRepository = paymentIntentRepository;
    }

    public Mono<PaymentIntentResponse> createPaymentIntent(CreatePaymentIntentRequest request) {
        log.info("[createPaymentIntent] Creating payment intent: {}", request);

        LocalDateTime now = LocalDateTime.now();
        String paymentIntentId = request.getId() != null ? request.getId() : "pi_" + UUID.randomUUID();

        PaymentIntent paymentIntent = PaymentIntent.builder()
                .id(paymentIntentId)
                .merchantId(request.getMerchantId())
                .customerId(request.getCustomerId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "CLP")
                .status(request.getStatus() != null ? request.getStatus() : "requires_payment_method")
                .captureMethod(request.getCaptureMethod() != null ? request.getCaptureMethod() : "automatic")
                .confirmationMethod(request.getConfirmationMethod() != null ? request.getConfirmationMethod() : "automatic")
                .description(request.getDescription())
                .statementDescriptor(request.getStatementDescriptor())
                .clientSecret(generateClientSecret(paymentIntentId))
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Set metadata usando el método helper
        paymentIntent.setMetadataFromString(request.getMetadata());

        return paymentIntentRepository.save(paymentIntent)
                .doOnSuccess(pi -> log.info("[createPaymentIntent] Payment intent created successfully: {}", pi.getId()))
                .doOnError(error -> log.error("[createPaymentIntent] Error creating payment intent", error))
                .map(this::toResponse);
    }

    public Mono<PaymentIntentResponse> getPaymentIntent(String id) {
        log.info("[getPaymentIntent] Fetching payment intent: {}", id);
        return paymentIntentRepository.findById(id)
                .map(this::toResponse)
                .doOnSuccess(pi -> log.info("[getPaymentIntent] Payment intent found: {}", id))
                .switchIfEmpty(Mono.error(new RuntimeException("Payment intent not found: " + id)));
    }

    public Flux<PaymentIntentResponse> getPaymentIntentsByMerchant(String merchantId) {
        log.info("[getPaymentIntentsByMerchant] Fetching payment intents for merchant: {}", merchantId);
        return paymentIntentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)
                .map(this::toResponse);
    }

    public Flux<PaymentIntentResponse> getPaymentIntentsByMerchantAndStatus(String merchantId, String status) {
        log.info("[getPaymentIntentsByMerchantAndStatus] Fetching payment intents for merchant: {} with status: {}", merchantId, status);
        return paymentIntentRepository.findByMerchantIdAndStatus(merchantId, status)
                .map(this::toResponse);
    }

    public Mono<PaymentIntentResponse> updatePaymentIntent(String id, UpdatePaymentIntentRequest request) {
        log.info("[updatePaymentIntent] Updating payment intent: {} with data: {}", id, request);

        return paymentIntentRepository.findById(id)
                .switchIfEmpty(Mono.error(new RuntimeException("Payment intent not found: " + id)))
                .flatMap(existing -> {
                    // Marcar como no nueva para forzar UPDATE
                    existing.setNew(false);

                    if (request.getStatus() != null) {
                        existing.setStatus(request.getStatus());
                    }
                    if (request.getCustomerId() != null) {
                        existing.setCustomerId(request.getCustomerId());
                    }
                    if (request.getAmount() != null) {
                        existing.setAmount(request.getAmount());
                    }
                    if (request.getCurrency() != null) {
                        existing.setCurrency(request.getCurrency());
                    }
                    if (request.getPaymentMethodId() != null) {
                        existing.setPaymentMethodId(request.getPaymentMethodId());
                    }
                    if (request.getDescription() != null) {
                        existing.setDescription(request.getDescription());
                    }
                    if (request.getStatementDescriptor() != null) {
                        existing.setStatementDescriptor(request.getStatementDescriptor());
                    }
                    if (request.getMetadata() != null) {
                        existing.setMetadataFromString(request.getMetadata());
                    }
                    if (request.getLastPaymentError() != null) {
                        existing.setLastPaymentErrorFromString(request.getLastPaymentError());
                    }

                    existing.setUpdatedAt(LocalDateTime.now());

                    return paymentIntentRepository.save(existing);
                })
                .doOnSuccess(pi -> log.info("[updatePaymentIntent] Payment intent updated successfully: {}", id))
                .doOnError(error -> log.error("[updatePaymentIntent] Error updating payment intent: {}", id, error))
                .map(this::toResponse);
    }

    public Mono<Void> cancelPaymentIntent(String id) {
        log.info("[cancelPaymentIntent] Canceling payment intent: {}", id);
        return updatePaymentIntent(id, UpdatePaymentIntentRequest.builder()
                        .status("canceled")
                        .build())
                .then();
    }

    public Mono<TransactionListResponse> getTransactionsForDashboard(
            String merchantId,
            String status,
            int page,
            int pageSize) {

        log.info("[getTransactionsForDashboard] Fetching transactions for merchant: {}, status: {}, page: {}, pageSize: {}",
                merchantId, status, page, pageSize);

        Flux<PaymentIntent> query;

        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("all")) {
            query = paymentIntentRepository.findByMerchantIdAndStatus(merchantId, status);
        } else {
            query = paymentIntentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId);
        }

        return query
                .collectList()
                .flatMap(allTransactions -> {
                    long totalCount = allTransactions.size();
                    int skip = page * pageSize;

                    // Paginar la lista
                    var paginatedTransactions = allTransactions.stream()
                            .skip(skip)
                            .limit(pageSize)
                            .map(this::toResponse)
                            .toList();

                    boolean hasMore = (skip + pageSize) < totalCount;

                    return Mono.just(TransactionListResponse.builder()
                            .transactions(paginatedTransactions)
                            .totalCount(totalCount)
                            .page(page)
                            .pageSize(pageSize)
                            .hasMore(hasMore)
                            .build());
                })
                .doOnSuccess(response -> log.info("[getTransactionsForDashboard] Retrieved {} transactions out of {} total",
                        response.getTransactions().size(), response.getTotalCount()));
    }

    private String generateClientSecret(String paymentIntentId) {
        return paymentIntentId + "_secret_" + UUID.randomUUID().toString().replace("-", "");
    }

    private PaymentIntentResponse toResponse(PaymentIntent paymentIntent) {
        return PaymentIntentResponse.builder()
                .id(paymentIntent.getId())
                .merchantId(paymentIntent.getMerchantId())
                .customerId(paymentIntent.getCustomerId())
                .amount(paymentIntent.getAmount())
                .currency(paymentIntent.getCurrency())
                .status(paymentIntent.getStatus())
                .paymentMethodId(paymentIntent.getPaymentMethodId())
                .captureMethod(paymentIntent.getCaptureMethod())
                .confirmationMethod(paymentIntent.getConfirmationMethod())
                .description(paymentIntent.getDescription())
                .statementDescriptor(paymentIntent.getStatementDescriptor())
                .metadata(paymentIntent.getMetadataAsString())
                .clientSecret(paymentIntent.getClientSecret())
                .lastPaymentError(paymentIntent.getLastPaymentErrorAsString())
                .createdAt(paymentIntent.getCreatedAt())
                .updatedAt(paymentIntent.getUpdatedAt())
                .build();
    }
}

