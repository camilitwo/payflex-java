package com.payflex.web;

import com.payflex.merchant.dto.CreateMerchantRequest;
import com.payflex.merchant.dto.MerchantConfigResponse;
import com.payflex.merchant.dto.MerchantResponse;
import com.payflex.merchant.dto.MerchantUserResponse;
import com.payflex.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/merchants")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MerchantResponse> createMerchant(@RequestBody CreateMerchantRequest request) {
        log.info("[MC][IN] POST /merchants businessName={} email={} userId={} merchantId={} role={}", request.getBusinessName(), request.getEmail(), request.getUserId(), request.getMerchantId(), request.getRole());
        return merchantService.createMerchant(request)
            .doOnSubscribe(s -> log.debug("[MC][FLOW] Subscribed createMerchant email={}", request.getEmail()))
            .doOnNext(resp -> log.info("[MC][OK] merchantId={} email={} availableBalance={}", resp.getMerchantId(), resp.getEmail(), resp.getAvailableBalance()))
            .doOnError(err -> log.error("[MC][ERR] email={} merchantIdCandidate={} msg={} class={}", request.getEmail(), request.getMerchantId(), err.getMessage(), err.getClass().getName(), err))
            .doFinally(sig -> log.debug("[MC][DONE] signal={} email={}", sig, request.getEmail()));
    }

    @GetMapping("/{merchantId}")
    public Mono<MerchantResponse> getMerchant(@PathVariable String merchantId) {
        log.info("Received request to get merchant: {}", merchantId);
        return merchantService.getMerchantById(merchantId);
    }

    @GetMapping
    public Flux<MerchantResponse> getAllMerchants() {
        log.info("Received request to get all merchants");
        return merchantService.getAllMerchants();
    }

    @PutMapping("/{merchantId}")
    public Mono<MerchantResponse> updateMerchant(
            @PathVariable String merchantId,
            @RequestBody CreateMerchantRequest request) {
        log.info("Received request to update merchant: {}", merchantId);
        return merchantService.updateMerchant(merchantId, request);
    }

    @DeleteMapping("/{merchantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deactivateMerchant(@PathVariable String merchantId) {
        log.info("Received request to deactivate merchant: {}", merchantId);
        return merchantService.deactivateMerchant(merchantId);
    }

    @GetMapping("/{merchantId}/validate")
    public Mono<Boolean> validateMerchant(@PathVariable String merchantId) {
        log.debug("Received request to validate merchant: {}", merchantId);
        return merchantService.validateMerchant(merchantId);
    }

    @GetMapping("/{merchantId}/users")
    public Flux<MerchantUserResponse> getMerchantUsers(@PathVariable String merchantId) {
        log.info("Received request to get merchant users: {}", merchantId);
        return merchantService.getMerchantUsers(merchantId);
    }

    @GetMapping("/{merchantId}/config")
    public Mono<MerchantConfigResponse> getMerchantConfig(@PathVariable String merchantId) {
        log.info("Received request to get merchant config: {}", merchantId);
        return merchantService.getMerchantConfig(merchantId);
    }
}
