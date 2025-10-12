package com.auth.util;

import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ClaimUtils {
    private ClaimUtils() {}

    public static Mono<Jwt> currentJwt() {
        return ReactiveSecurityContextHolder.getContext()
            .map(sc -> sc.getAuthentication())
            .filter(auth -> auth.getPrincipal() instanceof Jwt)
            .map(auth -> (Jwt) auth.getPrincipal());
    }

    public static Mono<String> currentUserId() {
        return currentJwt().map(Jwt::getSubject);
    }

    public static Mono<String> currentMerchantId() {
        return currentJwt().map(jwt -> jwt.getClaimAsString("merchantId"));
    }

    public static Mono<List<String>> currentRoles() {
        return currentJwt().map(jwt -> {
            Object val = jwt.getClaims().get("roles");
            if (val instanceof List<?> list) {
                return list.stream().map(String::valueOf).collect(Collectors.toList());
            }
            if (val instanceof String s && !s.isBlank()) {
                return List.of(s);
            }
            return List.of();
        });
    }

    public static Mono<Map<String,Object>> claims() {
        return currentJwt().map(Jwt::getClaims);
    }

    public static Mono<String> currentBearer() {
        return currentJwt().map(jwt -> "Bearer " + jwt.getTokenValue());
    }
}
