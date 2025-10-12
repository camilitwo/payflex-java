package com.auth.web;

import com.auth.client.MerchantServiceClient;
import com.auth.pb.PocketBaseClient;
import com.auth.pb.PocketBaseProperties;
import com.auth.security.JwkProvider;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;

import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
public class AuthController {

  private static final Logger log = LoggerFactory.getLogger(AuthController.class);

  private final JwkProvider jwkProvider;
  private final PocketBaseClient pb;
  private final PocketBaseProperties cfg;
  private final MerchantServiceClient merchantServiceClient;
  private final RSASSASigner signer; // Reusable signer

  public AuthController(JwkProvider jwkProvider, PocketBaseClient pb, PocketBaseProperties cfg, MerchantServiceClient merchantServiceClient){
    this.jwkProvider = jwkProvider;
    this.pb = pb;
    this.cfg = cfg;
    this.merchantServiceClient = merchantServiceClient;
    try {
      this.signer = new RSASSASigner(jwkProvider.getRsaKey().toPrivateKey());
    } catch (JOSEException e) {
      throw new IllegalStateException("Unable to initialize RSASSASigner: " + e.getMessage(), e);
    }
  }

  @Value("${auth.issuer}") String issuer;
  @Value("${auth.audience}") String audience;
  @Value("${auth.access-token-ttl-minutes}") long accessTtlMin;
  @Value("${auth.refresh-token-ttl-days}") long refreshTtlDays;
  @Value("${auth.cookie.name}") String cookieName;

  record LoginReq(@NotBlank String email, @NotBlank String password) {}
  record RegisterReq(
      @NotBlank String email,
      @NotBlank String password,
      @NotBlank String passwordConfirm,
      @NotBlank String name,
      String merchantId,
      List<String> roles
  ) {}
  record TokenRes(
      String accessToken,
      long expiresInSeconds,
      String userId,
      String merchantId,
      List<String> roles
  ) {}
  record UserRes(
      String id,
      String email,
      String name,
      String merchantId,
      List<String> roles,
      String created
  ) {}

  @GetMapping("/.well-known/jwks.json")
  public Map<String,Object> jwks(){ return jwkProvider.jwksJson(); }

  @PostMapping("/auth/register")
  public Mono<ResponseEntity<UserRes>> register(@RequestBody RegisterReq req){
    System.out.println("[AUTH][REGISTER] Iniciando registro email=" + req.email());

    Map<String, Object> userData = new LinkedHashMap<>();
    userData.put("email", req.email());
    userData.put("password", req.password());
    userData.put("passwordConfirm", req.passwordConfirm());
    userData.put("name", req.name());

    if (req.merchantId() != null && !req.merchantId().isBlank()) {
      System.out.println("[AUTH][REGISTER] merchantId provisto=" + req.merchantId());
      userData.put("merchantId", req.merchantId());
    }

    List<String> roles = req.roles() != null && !req.roles().isEmpty()
        ? req.roles()
        : List.of("MERCHANT_ADMIN");
    userData.put("roles", roles);
    System.out.println("[AUTH][REGISTER] Roles a asignar=" + roles);

    return pb.createUser(userData)
        .doOnSubscribe(s -> System.out.println("[AUTH][PB] Creando usuario en PocketBase..."))
        .doOnError(err -> System.err.println("[AUTH][PB][ERROR] Falló creación PB: " + err.getMessage()))
        .flatMap(response -> {
          System.out.println("[AUTH][PB] Usuario creado en PB: " + response);
          String userId = (String) response.get("id");
          String email = (String) response.get("email");
          String name = (String) response.get("name");
          String created = (String) response.get("created");

          String merchantId = (String) response.getOrDefault("merchantId", null);
          if (merchantId == null || merchantId.isBlank()) {
            merchantId = "mrc_" + userId.substring(0, Math.min(8, userId.length()));
            System.out.println("[AUTH][REGISTER] Generado merchantId=" + merchantId);
          }

          Object rolesObj = response.get("roles");
          @SuppressWarnings("unchecked")
          List<String> userRoles = rolesObj instanceof List<?>
              ? ((List<?>) rolesObj).stream().map(String::valueOf).toList()
              : roles;

          Map<String, Object> merchantRequest = new LinkedHashMap<>();
          merchantRequest.put("userId", userId);
          merchantRequest.put("merchantId", merchantId);
          merchantRequest.put("email", email);
          merchantRequest.put("businessName", name + "'s Business");
          merchantRequest.put("businessType", "RETAIL");
          merchantRequest.put("taxId", "PENDING");
          merchantRequest.put("phone", "");
          merchantRequest.put("address", "");
            merchantRequest.put("city", "");
          merchantRequest.put("country", "Chile");
          merchantRequest.put("role", userRoles.isEmpty() ? "MERCHANT_ADMIN" : userRoles.get(0));

          System.out.println("[AUTH][REGISTER] Llamando merchant-service payload=" + merchantRequest);

          final String finalMerchantId = merchantId;
          final List<String> finalRoles = userRoles;

          return merchantServiceClient.createMerchant(merchantRequest)
              .doOnSubscribe(s -> System.out.println("[AUTH][MS] Invocando merchant-service ..."))
              .doOnSuccess(m -> System.out.println("[AUTH][MS] Respuesta merchant-service=" + m))
              .doOnError(error -> System.err.println("[AUTH][MS][ERROR] merchant-service: " + error.getMessage()))
              .thenReturn(ResponseEntity.ok(new UserRes(userId, email, name, finalMerchantId, finalRoles, created)))
              .onErrorResume(error -> {
                System.err.println("[AUTH][REGISTER][WARN] Continuando sin merchant en PG: " + error.getMessage());
                return Mono.just(ResponseEntity.ok(new UserRes(userId, email, name, finalMerchantId, finalRoles, created)));
              });
        })
        .doOnSuccess(r -> System.out.println("[AUTH][REGISTER] Flujo completo OK userId=" + r.getBody().id()))
        .doOnError(err -> System.err.println("[AUTH][REGISTER][ERROR] Flujo falló: " + err.getMessage()));
  }

  @PostMapping("/auth/login")
  public Mono<ResponseEntity<TokenRes>> login(@RequestBody LoginReq req){
    log.info("🔵 [AUTH][LOGIN] Inicio - email={}", req.email());

    return pb.authWithPassword(req.email(), req.password())
        .doOnSubscribe(s -> log.info("🔵 [AUTH][LOGIN] Llamando PocketBase authWithPassword..."))
        .doOnError(err -> log.error("🔴 [AUTH][LOGIN][ERROR] PocketBase auth falló: {}", err.getMessage(), err))
        .map(res -> {
          log.info("✅ [AUTH][LOGIN] PocketBase auth OK, procesando record...");
          // PocketBase response has "record" object
          @SuppressWarnings("unchecked")
          Map<String,Object> record = (Map<String, Object>) res.get("record");
          String userId = (String) record.get("id");
          log.info("🔵 [AUTH][LOGIN] userId={}", userId);

          // merchantId & roles mapping
          String merchantId = (String) record.getOrDefault(cfg.getMerchantField(), null);
          // Si no tiene merchantId, generar uno basado en el userId
          if (merchantId == null || merchantId.isBlank()) {
            merchantId = "mrc_" + userId.substring(0, Math.min(8, userId.length()));
            log.info("🔵 [AUTH][LOGIN] merchantId generado={}", merchantId);
          } else {
            log.info("🔵 [AUTH][LOGIN] merchantId desde PB={}", merchantId);
          }

          Object rolesObj = record.get(cfg.getRolesField());
          List<String> roles = rolesObj instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of("MERCHANT_ADMIN");
          log.info("🔵 [AUTH][LOGIN] roles={}", roles);

          List<String> scopes = List.of("payments:read","payments:write");

          var now = Instant.now();
          var accessExp = now.plusSeconds(accessTtlMin * 60);

          log.info("🔵 [AUTH][LOGIN] Generando JWT...");
          var accessJwt = signJwt(userId, merchantId, roles, scopes, now, accessExp);
          var refreshOpaque = UUID.randomUUID().toString(); // TODO: persist later (Redis)

          ResponseCookie refreshCookie = ResponseCookie.from(cookieName, refreshOpaque)
              .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(refreshTtlDays*86400).build();

          log.info("✅ [AUTH][LOGIN] Login exitoso userId={} merchantId={}", userId, merchantId);

          return ResponseEntity.ok()
              .header("Set-Cookie", refreshCookie.toString())
              .body(new TokenRes(accessJwt, accessExp.getEpochSecond() - now.getEpochSecond(), userId, merchantId, roles));
        })
        .doOnError(err -> log.error("🔴 [AUTH][LOGIN][ERROR] Flujo completo falló: {}", err.getMessage(), err));
  }

  @PostMapping("/auth/refresh")
  public ResponseEntity<TokenRes> refresh(@CookieValue(name = "session", required = false) String refresh){
    if (refresh == null || refresh.isBlank()) return ResponseEntity.status(401).build();
    // Without Redis, we just issue a new access for demo purposes (do not use in prod).
    var now = Instant.now();
    var accessExp = now.plusSeconds(accessTtlMin * 60);
    var accessJwt = signJwt("user-unknown","mrc_unknown", List.of("MERCHANT_ADMIN"), List.of("payments:read","payments:write"), now, accessExp);
    return ResponseEntity.ok(new TokenRes(accessJwt, accessExp.getEpochSecond() - now.getEpochSecond(), "user-unknown", "mrc_unknown", List.of("MERCHANT_ADMIN")));
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(){
    ResponseCookie delete = ResponseCookie.from(cookieName, "")
        .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(0).build();
    return ResponseEntity.noContent().header("Set-Cookie", delete.toString()).build();
  }

  private String signJwt(String userId, String merchantId, List<String> roles, List<String> scopes, Instant iat, Instant exp){
    try {
      Map<String,Object> claims = new LinkedHashMap<>();
      claims.put("iss", issuer);
      claims.put("aud", audience);
      claims.put("sub", userId);
      claims.put("merchantId", merchantId);
      claims.put("roles", roles);
      claims.put("scopes", scopes);
      claims.put("jti", UUID.randomUUID().toString());
      claims.put("iat", iat.getEpochSecond());
      claims.put("nbf", iat.getEpochSecond());
      claims.put("exp", exp.getEpochSecond());

      var header = new JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.RS256)
          .keyID(jwkProvider.getRsaKey().getKeyID())
          .type(JOSEObjectType.JWT)
          .build();

      var jws = new JWSObject(header, new Payload(claims));
      jws.sign(signer); // Puede lanzar JOSEException
      return jws.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("JWT_SIGN_ERROR: " + e.getMessage(), e);
    }
  }
}
