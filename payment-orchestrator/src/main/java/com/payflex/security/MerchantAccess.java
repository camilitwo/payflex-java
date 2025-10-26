package com.payflex.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class MerchantAccess {
  private static final Logger log = LoggerFactory.getLogger(MerchantAccess.class);

  public void ensure(Authentication auth, String targetMerchantId){
    log.debug("Checking merchant access. Target merchantId: {}", targetMerchantId);
    log.debug("Authentication principal type: {}", auth.getPrincipal().getClass().getName());

    Jwt jwt = (Jwt) auth.getPrincipal();
    String merchantId = jwt.getClaimAsString("merchantId");

    log.debug("JWT merchantId claim: {}", merchantId);
    log.debug("JWT all claims: {}", jwt.getClaims());

    if (merchantId == null || !merchantId.equals(targetMerchantId)){
      log.warn("Access denied! JWT merchantId: '{}' does not match target merchantId: '{}'", merchantId, targetMerchantId);
      throw new org.springframework.security.access.AccessDeniedException("Forbidden for this merchant");
    }

    log.debug("Merchant access granted for merchantId: {}", merchantId);
  }
}
