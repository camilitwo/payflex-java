package com.auth.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.text.ParseException;
import java.util.Map;

@Component
public class JwkProvider {
  private static final Logger log = LoggerFactory.getLogger(JwkProvider.class);

  private final RSAKey rsaKey;

  public JwkProvider(Environment env) {
    this.rsaKey = loadRsaKey(env);
  }

  private RSAKey loadRsaKey(Environment env) {
    // 1) Intentar cargar desde variable de entorno/propiedad: AUTH_JWK_JSON (JWK RSA con "d" incluida)
    String jwkJson = firstNonBlank(
        env.getProperty("AUTH_JWK_JSON"),
        System.getenv("AUTH_JWK_JSON")
    );
    if (jwkJson != null && !jwkJson.isBlank()) {
      try {
        RSAKey key = RSAKey.parse(jwkJson);
        if (key.isPrivate()) {
          log.info("[JwkProvider] Using RSA key from AUTH_JWK_JSON (kid={})", safeKid(key));
          // Asegurar uso/alg si faltan
          RSAKey.Builder b = new RSAKey.Builder(key.toRSAPublicKey())
              .privateKey(key.toPrivateKey())
              .keyID(key.getKeyID());
          if (key.getKeyUse() == null) b.keyUse(KeyUse.SIGNATURE);
          if (key.getAlgorithm() == null) b.algorithm(JWSAlgorithm.RS256);
          return b.build();
        } else {
          log.warn("[JwkProvider] AUTH_JWK_JSON no contiene clave privada; se generará una temporal.");
        }
      } catch (ParseException e) {
        log.error("[JwkProvider] AUTH_JWK_JSON inválido: {}. Se generará una clave temporal.", e.getMessage());
      }
    }

    // 2) Fallback: generar par RSA efímero (no recomendado en producción multi-instancia)
    try {
      var kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      KeyPair kp = kpg.generateKeyPair();
      RSAKey gen = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) kp.getPublic())
          .privateKey(kp.getPrivate())
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(JWSAlgorithm.RS256)
          .keyIDFromThumbprint()
          .build();
      log.warn("[JwkProvider] Generated ephemeral RSA key (kid={}). Set AUTH_JWK_JSON to persist.", safeKid(gen));
      return gen;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String firstNonBlank(String... vals) {
    if (vals == null) return null;
    for (String v : vals) { if (v != null && !v.isBlank()) return v; }
    return null;
  }

  private String safeKid(RSAKey k) { return k == null ? null : k.getKeyID(); }

  public RSAKey getRsaKey(){ return rsaKey; }
  public JWKSet jwkSet(){ return new JWKSet(rsaKey.toPublicJWK()); }
  public Map<String, Object> jwksJson(){ return jwkSet().toJSONObject(true); }
}
