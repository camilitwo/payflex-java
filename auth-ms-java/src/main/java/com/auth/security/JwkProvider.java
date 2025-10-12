package com.auth.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Map;

@Component
public class JwkProvider {
  private final RSAKey rsaKey;
  public JwkProvider() {
    try {
      var kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      KeyPair kp = kpg.generateKeyPair();
      this.rsaKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) kp.getPublic())
          .privateKey(kp.getPrivate())
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(JWSAlgorithm.RS256)
          .keyIDFromThumbprint()
          .build();
    } catch (Exception e) { throw new IllegalStateException(e); }
  }
  public RSAKey getRsaKey(){ return rsaKey; }
  public JWKSet jwkSet(){ return new JWKSet(rsaKey.toPublicJWK()); }
  public Map<String, Object> jwksJson(){ return jwkSet().toJSONObject(true); }
}
