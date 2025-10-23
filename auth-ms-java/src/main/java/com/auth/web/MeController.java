package com.auth.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoints /me simples basados en los claims del JWT.
 * Estos sirven a los scripts de test mientras se implementa la integración real con merchant-service.
 */
@RestController
public class MeController {

  @GetMapping(value = "/me/merchant", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String,Object>>> merchant(@AuthenticationPrincipal Jwt jwt) {
    return Mono.fromSupplier(() -> {
      if (jwt == null) return ResponseEntity.status(401).build();
      String merchantId = optionalString(jwt, "merchantId");
      if (merchantId == null || merchantId.isBlank()) {
        // Fallback derivado del sub (igual que en AuthController login)
        String sub = optionalString(jwt, "sub");
        if (sub != null && !sub.isBlank()) {
          merchantId = "mrc_" + sub.substring(0, Math.min(sub.length(), 8));
        }
      }
      Map<String,Object> body = new LinkedHashMap<>();
      body.put("merchantId", merchantId);
      body.put("status", "active");
      body.put("source", "jwt-only");
      return ResponseEntity.ok(body);
    });
  }

  @GetMapping(value = "/me/merchant/users", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<List<Map<String,Object>>>> merchantUsers(@AuthenticationPrincipal Jwt jwt) {
    return Mono.fromSupplier(() -> {
      if (jwt == null) return ResponseEntity.status(401).build();
      String userId = optionalString(jwt, "sub");
      @SuppressWarnings("unchecked")
      List<String> roles = (List<String>) jwt.getClaims().getOrDefault("roles", List.of("MERCHANT_ADMIN"));
      Map<String,Object> user = new LinkedHashMap<>();
      user.put("userId", userId);
      user.put("email", optionalString(jwt, "email")); // puede que sea null si no se incluyó
      user.put("roles", roles);
      user.put("merchantId", optionalString(jwt, "merchantId"));
      return ResponseEntity.ok(List.of(user));
    });
  }

  @GetMapping(value = "/me/merchant/config", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String,Object>>> merchantConfig(@AuthenticationPrincipal Jwt jwt) {
    return Mono.fromSupplier(() -> {
      if (jwt == null) return ResponseEntity.status(401).build();
      String merchantId = optionalString(jwt, "merchantId");
      Map<String,Object> cfg = new LinkedHashMap<>();
      cfg.put("merchantId", merchantId);
      cfg.put("defaultCurrency", "CLP");
      cfg.put("paymentMethodsEnabled", "[\"card\",\"transfer\"]"); // string json (mantener compat con script existente)
      cfg.put("autoCapture", Boolean.TRUE);
      cfg.put("webhookUrl", null);
      cfg.put("statementDescriptor", null);
      cfg.put("createdAt", null);
      cfg.put("updatedAt", null);
      return ResponseEntity.ok(cfg);
    });
  }

  @GetMapping(value = "/me/merchant/summary", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String,Object>>> merchantSummary(@AuthenticationPrincipal Jwt jwt) {
    return Mono.fromSupplier(() -> {
      if (jwt == null) return ResponseEntity.status(401).build();
      Map<String,Object> summary = new LinkedHashMap<>();
      summary.put("merchantId", optionalString(jwt, "merchantId"));
      summary.put("totalUsers", 1);
      summary.put("totalPaymentIntents", 0);
      summary.put("totalVolumeMinorUnits", 0);
      summary.put("currency", "CLP");
      summary.put("generatedAt", Instant.now().toString());
      return ResponseEntity.ok(summary);
    });
  }

  @GetMapping(value = "/me/merchant/dashboard/stats", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<ResponseEntity<Map<String,Object>>> dashboardStats(@AuthenticationPrincipal Jwt jwt) {
    return Mono.fromSupplier(() -> {
      if (jwt == null) return ResponseEntity.status(401).build();

      // Estructura de respuesta con estadísticas del dashboard
      Map<String,Object> response = new LinkedHashMap<>();

      // Transacciones
      Map<String,Object> transactions = new LinkedHashMap<>();
      transactions.put("count", 1234L);
      transactions.put("percentageChange", 12.0);
      response.put("transactions", transactions);

      // Ingresos
      Map<String,Object> income = new LinkedHashMap<>();
      income.put("amount", 45678.00);
      income.put("currency", "USD");
      income.put("percentageChange", 8.0);
      response.put("income", income);

      // Crecimiento
      Map<String,Object> growth = new LinkedHashMap<>();
      growth.put("percentage", 23.0);
      growth.put("percentageChange", 5.0);
      response.put("growth", growth);

      return ResponseEntity.ok(response);
    });
  }

  private String optionalString(Jwt jwt, String claim) {
    Object v = jwt.getClaims().get(claim);
    return v == null ? null : String.valueOf(v);
  }
}

