package com.payflex.merchant.service;

import com.payflex.merchant.dto.CreateMerchantRequest;
import com.payflex.merchant.dto.MerchantResponse;
import com.payflex.merchant.dto.MerchantConfigResponse;
import com.payflex.merchant.dto.MerchantUserResponse;
import com.payflex.merchant.model.Merchant;
import com.payflex.merchant.model.MerchantBalance;
import com.payflex.merchant.model.MerchantPaymentConfig;
import com.payflex.merchant.model.MerchantUser;
import com.payflex.merchant.repository.MerchantBalanceRepository;
import com.payflex.merchant.repository.MerchantPaymentConfigRepository;
import com.payflex.merchant.repository.MerchantRepository;
import com.payflex.merchant.repository.MerchantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final MerchantBalanceRepository balanceRepository;
    private final MerchantUserRepository merchantUserRepository;
    private final MerchantPaymentConfigRepository paymentConfigRepository;

    @Transactional
    public Mono<MerchantResponse> createMerchant(CreateMerchantRequest request) {
        log.info("[MS][CREATE] start userId={} providedMerchantId={} email={} businessName={}", request.getUserId(), request.getMerchantId(), request.getEmail(), request.getBusinessName());

        String providedMerchantId = hasText(request.getMerchantId()) ? request.getMerchantId() : null;
        String incomingEmail = hasText(request.getEmail()) ? request.getEmail() : null;

        Mono<Merchant> existingMono;
        if (providedMerchantId != null) {
            existingMono = merchantRepository.findById(providedMerchantId)
                .doOnSubscribe(s -> log.debug("[MS][LOOKUP] by merchantId={} ...", providedMerchantId))
                .doOnNext(m -> log.debug("[MS][LOOKUP] found existing merchantId={} email={}", m.getId(), m.getEmail()));
        } else if (incomingEmail != null) {
            existingMono = merchantRepository.findByEmail(incomingEmail)
                .doOnSubscribe(s -> log.debug("[MS][LOOKUP] by email={} ...", incomingEmail))
                .doOnNext(m -> log.debug("[MS][LOOKUP] found existing by email merchantId={}", m.getId()));
        } else {
            existingMono = Mono.empty(); // no criteria => will create new
        }

        return existingMono
            .flatMap(existing -> {
                log.info("[MS][PATH] existing merchant branch merchantId={} email={}", existing.getId(), existing.getEmail());
                return ensureMerchantUser(existing.getId(), request)
                    .then(attachBalance(existing));
            })
            .switchIfEmpty(Mono.defer(() -> {
                // Determinar merchantId primero
                String merchantId = providedMerchantId != null ? providedMerchantId : generateMerchantId();
                // Defaults
                String businessName = hasText(request.getBusinessName()) ? request.getBusinessName() : ("Merchant " + merchantId);
                String legalName = hasText(request.getLegalName()) ? request.getLegalName() : businessName;
                String email = incomingEmail != null ? incomingEmail : (merchantId + "@merchant.local");
                String taxId = hasText(request.getTaxId()) ? request.getTaxId() : ("PENDING-" + merchantId);
                String phone = hasText(request.getPhone()) ? request.getPhone() : null;
                String website = hasText(request.getWebsite()) ? request.getWebsite() : null;
                String status = "active";
                Boolean onboardingCompleted = false;

                Merchant merchant = Merchant.builder()
                    .id(merchantId)
                    .businessName(businessName)
                    .legalName(legalName)
                    .taxId(taxId)
                    .email(email)
                    .phone(phone)
                    .website(website)
                    .status(status)
                    .onboardingCompleted(onboardingCompleted)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

                return merchantRepository.save(merchant)
                    .doOnSuccess(m -> log.info("[MS][SAVE] merchant persisted merchantId={} email={}", m.getId(), m.getEmail()))
                    .flatMap(saved -> {
                        request.applyFallbacks(saved.getId(), saved.getBusinessName(), saved.getEmail());
                        return createInitialBalance(saved.getId())
                            .then(createInitialPaymentConfig(saved.getId()))
                            .then(ensureMerchantUser(saved.getId(), request))
                            .then(attachBalance(saved));
                    });
            }))
            .doOnError(err -> log.error("[MS][ERROR] createMerchant failed userId={} msg={}", request.getUserId(), err.getMessage(), err))
            .doOnSuccess(resp -> log.info("[MS][SUCCESS] merchantId={} email={} availableBalance={}", resp.getMerchantId(), resp.getEmail(), resp.getAvailableBalance()))
            .doFinally(sig -> log.debug("[MS][DONE] signal={} userId={} merchantIdProvided={} emailProvided={}", sig, request.getUserId(), request.getMerchantId(), request.getEmail()));
    }

    private boolean hasText(String v){ return v != null && !v.isBlank(); }
    private String generateMerchantId(){ return "mrc_" + UUID.randomUUID().toString().replace("-", "").substring(0,12); }

    private Mono<Void> ensureMerchantUser(String merchantId, CreateMerchantRequest request) {
        log.debug("[MS][USER-LINK] start merchantId={} userId={} role={}", merchantId, request.getUserId(), request.getRole());
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            log.debug("[MS][USER-LINK] no userId provided skip");
            return Mono.empty();
        }
        String role = (request.getRole() != null && !request.getRole().isBlank()) ? request.getRole() : "MERCHANT_ADMIN";
        String status = "active";
        Boolean emailVerified = false;
        LocalDateTime now = LocalDateTime.now();
        String effectiveEmail = (request.getEmail() != null && !request.getEmail().isBlank()) ? request.getEmail() : (merchantId + "@merchant.local");
        String effectiveName = (request.getBusinessName() != null && !request.getBusinessName().isBlank()) ? request.getBusinessName() : ("Merchant " + merchantId);
        String passwordHash = request.getPasswordHash() != null ? request.getPasswordHash() : "";

        return merchantUserRepository.upsertMerchantUser(
                request.getUserId(),
                merchantId,
                effectiveEmail,
                passwordHash,
                effectiveName,
                request.getPhone(),
                role,
                status,
                emailVerified,
                null, // lastLoginAt
                now, // createdAt
                now  // updatedAt
            )
            .doOnSuccess(v -> log.info("[MS][USER-LINK] upserted userId={} merchantId={} role={}", request.getUserId(), merchantId, role))
            .doOnError(err -> log.error("[MS][USER-LINK][ERR] userId={} merchantId={} msg={}", request.getUserId(), merchantId, err.getMessage(), err));
    }

    private Mono<MerchantResponse> attachBalance(Merchant merchant) {
        return balanceRepository.findByMerchantId(merchant.getId())
            .defaultIfEmpty(MerchantBalance.builder()
                .merchantId(merchant.getId())
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .currency("CLP")
                .updatedAt(LocalDateTime.now())
                .build())
            .map(balance -> mapToResponse(merchant, balance));
    }

    private Mono<MerchantBalance> createInitialBalance(String merchantId) {
        log.debug("[MS][BALANCE] creating initial balance merchantId={}", merchantId);
        MerchantBalance balance = MerchantBalance.builder()
            .merchantId(merchantId)
            .availableBalance(BigDecimal.ZERO)
            .pendingBalance(BigDecimal.ZERO)
            .currency("CLP")
            .updatedAt(LocalDateTime.now())
            .build();
        return balanceRepository.save(balance)
            .doOnSuccess(b -> log.info("[MS][BALANCE] initial balance saved merchantId={}", merchantId))
            .doOnError(err -> log.error("[MS][BALANCE][ERR] merchantId={} msg={}", merchantId, err.getMessage(), err));
    }

    private Mono<Void> createInitialPaymentConfig(String merchantId) {
        log.debug("[MS][PAYMENT-CONFIG] creating initial payment config merchantId={}", merchantId);
        String apiKey = "sk_" + UUID.randomUUID().toString().replace("-", "");
        MerchantPaymentConfig config = MerchantPaymentConfig.builder()
            .merchantId(merchantId)
            .apiKeyHash(hashApiKey(apiKey))
            .defaultCurrency("CLP")
            .paymentMethodsEnabled("[\"card\", \"transfer\"]")
            .autoCapture(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        return paymentConfigRepository.save(config)
            .doOnSuccess(c -> log.info("[MS][PAYMENT-CONFIG] initial config saved merchantId={}", merchantId))
            .doOnError(err -> log.error("[MS][PAYMENT-CONFIG][ERR] merchantId={} msg={}", merchantId, err.getMessage(), err))
            .then();
    }

    private String hashApiKey(String apiKey) {
        // Implementar hash real en producción
        return "hashed_" + apiKey;
    }

    public Mono<MerchantResponse> getMerchantById(String merchantId) {
        log.debug("Getting merchant by ID: {}", merchantId);

        return merchantRepository.findById(merchantId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Merchant not found: " + merchantId)))
            .flatMap(merchant -> balanceRepository.findByMerchantId(merchantId)
                .defaultIfEmpty(MerchantBalance.builder()
                    .merchantId(merchantId)
                    .availableBalance(BigDecimal.ZERO)
                    .pendingBalance(BigDecimal.ZERO)
                    .currency("CLP")
                    .build())
                .map(balance -> mapToResponse(merchant, balance))
            );
    }

    public Flux<MerchantResponse> getAllMerchants() {
        log.debug("Getting all merchants");

        return merchantRepository.findAll()
            .flatMap(merchant -> balanceRepository.findByMerchantId(merchant.getId())
                .defaultIfEmpty(MerchantBalance.builder()
                    .merchantId(merchant.getId())
                    .availableBalance(BigDecimal.ZERO)
                    .pendingBalance(BigDecimal.ZERO)
                    .currency("CLP")
                    .build())
                .map(balance -> mapToResponse(merchant, balance))
            );
    }

    public Mono<MerchantResponse> updateMerchant(String merchantId, CreateMerchantRequest request) {
        log.info("Updating merchant: {}", merchantId);

        return merchantRepository.findById(merchantId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Merchant not found: " + merchantId)))
            .flatMap(merchant -> {
                merchant.setBusinessName(request.getBusinessName());
                merchant.setTaxId(request.getTaxId());
                merchant.setPhone(request.getPhone());
                merchant.setUpdatedAt(LocalDateTime.now());

                return merchantRepository.save(merchant);
            })
            .flatMap(merchant -> balanceRepository.findByMerchantId(merchantId)
                .defaultIfEmpty(MerchantBalance.builder()
                    .merchantId(merchantId)
                    .availableBalance(BigDecimal.ZERO)
                    .pendingBalance(BigDecimal.ZERO)
                    .currency("CLP")
                    .build())
                .map(balance -> mapToResponse(merchant, balance))
            );
    }

    public Mono<Void> deactivateMerchant(String merchantId) {
        log.info("Deactivating merchant: {}", merchantId);

        return merchantRepository.findById(merchantId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Merchant not found: " + merchantId)))
            .flatMap(merchant -> {
                merchant.setUpdatedAt(LocalDateTime.now());
                return merchantRepository.save(merchant);
            })
            .then();
    }

    public Mono<Boolean> validateMerchant(String merchantId) {
        log.debug("Validating merchant: {}", merchantId);
        return merchantRepository.findById(merchantId)
            .map(merchant -> "active".equalsIgnoreCase(merchant.getStatus()))
            .defaultIfEmpty(false);
    }

    public Flux<MerchantUserResponse> getMerchantUsers(String merchantId) {
        return merchantUserRepository.findByMerchantId(merchantId)
            .map(mu -> MerchantUserResponse.builder()
                .userId(mu.getUserId())
                .merchantId(mu.getMerchantId())
                .email(mu.getEmail())
                .name(mu.getName())
                .phone(mu.getPhone())
                .role(mu.getRole())
                .status(mu.getStatus())
                .emailVerified(Boolean.TRUE.equals(mu.getEmailVerified()))
                .lastLoginAt(mu.getLastLoginAt())
                .createdAt(mu.getCreatedAt())
                .updatedAt(mu.getUpdatedAt())
                .build());
    }

    public Mono<MerchantConfigResponse> getMerchantConfig(String merchantId) {
        return paymentConfigRepository.findByMerchantId(merchantId)
            .map(cfg -> MerchantConfigResponse.builder()
                .merchantId(cfg.getMerchantId())
                .defaultCurrency(cfg.getDefaultCurrency())
                .paymentMethodsEnabled(cfg.getPaymentMethodsEnabled())
                .autoCapture(cfg.getAutoCapture())
                .webhookUrl(cfg.getWebhookUrl())
                .statementDescriptor(cfg.getStatementDescriptor())
                .createdAt(cfg.getCreatedAt())
                .updatedAt(cfg.getUpdatedAt())
                .build())
            .switchIfEmpty(Mono.just(MerchantConfigResponse.builder()
                .merchantId(merchantId)
                .defaultCurrency("CLP")
                .paymentMethodsEnabled("[\"card\",\"transfer\"]")
                .autoCapture(true)
                .build()));
    }

    private MerchantResponse mapToResponse(Merchant merchant, MerchantBalance balance) {
        return MerchantResponse.builder()
            .merchantId(merchant.getId())
            .businessName(merchant.getBusinessName())
            .legalName(merchant.getLegalName())
            .taxId(merchant.getTaxId())
            .email(merchant.getEmail())
            .phone(merchant.getPhone())
            .website(merchant.getWebsite())
            .status(merchant.getStatus())
            .onboardingCompleted(merchant.getOnboardingCompleted())
            .createdAt(merchant.getCreatedAt())
            .updatedAt(merchant.getUpdatedAt())
            .availableBalance(balance.getAvailableBalance())
            .pendingBalance(balance.getPendingBalance())
            .currency(balance.getCurrency())
            .build();
    }
}
