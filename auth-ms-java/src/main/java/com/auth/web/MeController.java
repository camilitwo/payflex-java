package com.auth.web;

import com.auth.client.MerchantQueryClient;
import com.auth.dto.CreatePaymentIntentRequest;
import com.auth.dto.CreateWithdrawalRequest;
import com.auth.dto.DashboardStatsDto;
import com.auth.dto.MeMerchantConfigDto;
import com.auth.dto.MeMerchantDto;
import com.auth.dto.MeMerchantUserDto;
import com.auth.dto.MerchantBalanceDto;
import com.auth.dto.PaymentIntentDto;
import com.auth.dto.TransactionListDto;
import com.auth.dto.WithdrawalDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints /me que integran con merchant-service para obtener datos reales de la base de datos.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MeController {

  private final MerchantQueryClient merchantQueryClient;

  @GetMapping(value = "/me/merchant", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<MeMerchantDto>> merchant(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims");
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Obteniendo merchant desde DB: merchantId={}", merchantId);

    return merchantQueryClient.getMerchant(merchantId, bearer)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnError(e -> log.error("[MeController] Error obteniendo merchant: {}", e.getMessage(), e));
  }

  @GetMapping(value = "/me/merchant/users", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<List<MeMerchantUserDto>>> merchantUsers(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/users");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para /users");
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Obteniendo usuarios desde DB: merchantId={}", merchantId);

    return merchantQueryClient.getMerchantUsers(merchantId, bearer)
            .collectList()
            .map(users -> {
              if (users.isEmpty()) {
                log.info("[MeController] No se encontraron usuarios para merchantId={}", merchantId);
              }
              return ResponseEntity.ok(users);
            })
            .defaultIfEmpty(ResponseEntity.ok(List.of()))
            .doOnError(e -> log.error("[MeController] Error obteniendo usuarios: {}", e.getMessage(), e));
  }

  @GetMapping(value = "/me/merchant/config", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<MeMerchantConfigDto>> merchantConfig(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/config");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para /config");
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Obteniendo configuración desde DB: merchantId={}", merchantId);

    return merchantQueryClient.getMerchantConfig(merchantId, bearer)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnError(e -> log.error("[MeController] Error obteniendo config: {}", e.getMessage(), e));
  }

  @GetMapping(value = "/me/merchant/summary", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String, Object>>> merchantSummary(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/summary");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para /summary");
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Obteniendo summary desde DB: merchantId={}", merchantId);

    // Combinar datos de merchant y usuarios
    Mono<MeMerchantDto> merchantMono = merchantQueryClient.getMerchant(merchantId, bearer)
            .defaultIfEmpty(MeMerchantDto.builder().merchantId(merchantId).build());

    Mono<Long> userCountMono = merchantQueryClient.getMerchantUsers(merchantId, bearer)
            .count()
            .defaultIfEmpty(0L);

    return Mono.zip(merchantMono, userCountMono)
            .map(tuple -> {
              MeMerchantDto merchant = tuple.getT1();
              Long userCount = tuple.getT2();

              Map<String, Object> summary = new LinkedHashMap<>();
              summary.put("merchantId", merchant.getMerchantId());
              summary.put("businessName", merchant.getBusinessName());
              summary.put("status", merchant.getStatus());
              summary.put("totalUsers", userCount);
              summary.put("availableBalance", merchant.getAvailableBalance());
              summary.put("pendingBalance", merchant.getPendingBalance());
              summary.put("currency", merchant.getCurrency());
              summary.put("onboardingCompleted", merchant.getOnboardingCompleted());
              summary.put("generatedAt", Instant.now().toString());

              return ResponseEntity.ok(summary);
            })
            .doOnError(e -> log.error("[MeController] Error obteniendo summary: {}", e.getMessage(), e));
  }

  @GetMapping(value = "/me/merchant/dashboard/stats", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<DashboardStatsDto>> dashboardStats(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/dashboard/stats");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para /dashboard/stats");
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Obteniendo dashboard stats desde DB: merchantId={}", merchantId);

    return merchantQueryClient.getDashboardStats(merchantId, bearer)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnError(e -> log.error("[MeController] Error obteniendo dashboard stats: {}", e.getMessage(), e));

  }

  @GetMapping(value = "/me/merchant/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<TransactionListDto>> getTransactions(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
          @RequestParam(required = false, defaultValue = "all") String status,
          @RequestParam(required = false, defaultValue = "0") int page,
          @RequestParam(required = false, defaultValue = "10") int pageSize) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/transactions");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para /transactions");
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Obteniendo transacciones desde DB: merchantId={}, status={}, page={}, pageSize={}",
            merchantId, status, page, pageSize);

    return merchantQueryClient.getTransactions(merchantId, bearer, status, page, pageSize)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnError(e -> log.error("[MeController] Error obteniendo transacciones: {}", e.getMessage(), e));
  }

  @PostMapping(value = "/me/merchant/transactions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<PaymentIntentDto>> createPaymentIntent(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
          @RequestBody CreatePaymentIntentRequest request) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/transactions POST");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para crear transaction");
      return Mono.just(ResponseEntity.status(400).build());
    }

    request.setMerchantId(merchantId);


    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Creando PaymentIntent desde BFF: merchantId={}, amount={}, currency={}",
            merchantId, request.getAmount(), request.getCurrency());

    return merchantQueryClient.createPaymentIntent(request, bearer)
            .map(paymentIntent -> ResponseEntity.status(HttpStatus.CREATED).body(paymentIntent))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
            .doOnError(e -> log.error("[MeController] Error creando PaymentIntent: {}", e.getMessage(), e));
  }

  // ==================== WITHDRAWALS ====================

  /**
   * Obtener el balance disponible del merchant
   */
  @GetMapping(value = "/me/merchant/balance", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<MerchantBalanceDto>> getBalance(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/balance");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para /balance");
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Obteniendo balance desde DB: merchantId={}", merchantId);

    return merchantQueryClient.getMerchantBalance(merchantId, bearer)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnError(e -> log.error("[MeController] Error obteniendo balance: {}", e.getMessage(), e));
  }

  /**
   * Crear un retiro de dinero (withdrawal)
   */
  @PostMapping(value = "/me/merchant/withdrawals", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<WithdrawalDto>> createWithdrawal(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
          @RequestBody CreateWithdrawalRequest request) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/withdrawals POST");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para crear withdrawal");
      return Mono.just(ResponseEntity.status(400).build());
    }

    if (request.getPaymentIntentId() == null || request.getPaymentIntentId().isBlank()) {
      log.warn("[MeController] paymentIntentId requerido para crear withdrawal");
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Creando withdrawal desde BFF: merchantId={}, paymentIntentId={}, amount={}",
            merchantId, request.getPaymentIntentId(), request.getAmount());

    return merchantQueryClient.createWithdrawal(request.getPaymentIntentId(), request, bearer)
            .map(withdrawal -> ResponseEntity.status(HttpStatus.CREATED).body(withdrawal))
            .switchIfEmpty(Mono.fromSupplier(() -> {
              log.warn("[MeController] Downstream devolvió cuerpo vacío al crear withdrawal. Devolviendo 201 sin body.");
              return ResponseEntity.status(HttpStatus.CREATED).build();
            }))
            .onErrorResume(e -> {
              log.error("[MeController] Error creando withdrawal (propagación controlada): {}", e.getMessage(), e);
              return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).build());
            });
  }

  /**
   * Listar retiros del merchant (con filtro opcional por status)
   */
  @GetMapping(value = "/me/merchant/withdrawals", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<List<WithdrawalDto>>> getWithdrawals(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
          @RequestParam(required = false, defaultValue = "all") String status) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/withdrawals");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para /withdrawals");
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Obteniendo withdrawals desde DB: merchantId={}, status={}", merchantId, status);

    return merchantQueryClient.getWithdrawals(merchantId, bearer, status)
            .collectList()
            .map(withdrawals -> {
              log.info("[MeController] Se encontraron {} withdrawals para merchantId={}", withdrawals.size(), merchantId);
              return ResponseEntity.ok(withdrawals);
            })
            .defaultIfEmpty(ResponseEntity.ok(List.of()))
            .doOnError(e -> log.error("[MeController] Error obteniendo withdrawals: {}", e.getMessage(), e));
  }

  /**
   * Obtener un withdrawal específico por ID
   */
  @GetMapping(value = "/me/merchant/withdrawals/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<WithdrawalDto>> getWithdrawal(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
          @PathVariable String id) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/withdrawals/{id}");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para /withdrawals/{}", id);
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Obteniendo withdrawal desde DB: withdrawalId={}, merchantId={}", id, merchantId);

    return merchantQueryClient.getWithdrawal(id, bearer)
            .map(ResponseEntity::ok)
            .defaultIfEmpty(ResponseEntity.notFound().build())
            .doOnError(e -> log.error("[MeController] Error obteniendo withdrawal: {}", e.getMessage(), e));
  }

  /**
   * Cancelar un withdrawal pendiente
   */
  @PostMapping(value = "/me/merchant/withdrawals/{id}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Void>> cancelWithdrawal(
          @AuthenticationPrincipal Jwt jwt,
          @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
          @PathVariable String id) {

    if (jwt == null) {
      log.warn("[MeController] JWT no presente en /me/merchant/withdrawals/{id}/cancel");
      return Mono.just(ResponseEntity.status(401).build());
    }

    String merchantId = extractMerchantId(jwt);
    if (merchantId == null || merchantId.isBlank()) {
      log.warn("[MeController] merchantId no encontrado en JWT claims para cancelar withdrawal");
      return Mono.just(ResponseEntity.status(400).build());
    }

    String bearer = (authHeader != null && authHeader.startsWith("Bearer "))
            ? authHeader
            : "Bearer " + jwt.getTokenValue();

    log.info("[MeController] Cancelando withdrawal: withdrawalId={}, merchantId={}", id, merchantId);

    return merchantQueryClient.cancelWithdrawal(id, bearer)
            .then(Mono.just(ResponseEntity.ok().<Void>build()))
            .defaultIfEmpty(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build())
            .doOnError(e -> log.error("[MeController] Error cancelando withdrawal: {}", e.getMessage(), e));
  }



  /**
   * Extrae el merchantId del JWT. Primero intenta obtenerlo del claim "merchantId",
   * si no existe, lo deriva del "sub" (userId).
   */
  private String extractMerchantId(Jwt jwt) {
    Object merchantIdObj = jwt.getClaims().get("merchantId");
    if (merchantIdObj != null) {
      return String.valueOf(merchantIdObj);
    }

    // Fallback: derivar del sub (igual que en AuthController)
    Object subObj = jwt.getClaims().get("sub");
    if (subObj != null) {
      String sub = String.valueOf(subObj);
      if (!sub.isBlank()) {
        return "mrc_" + sub.substring(0, Math.min(sub.length(), 8));
      }
    }

    return null;
  }
}

