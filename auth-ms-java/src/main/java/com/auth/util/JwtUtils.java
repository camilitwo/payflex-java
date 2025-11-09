package com.auth.util;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utilidades para trabajar con JWT y extraer información de los claims.
 */
public class JwtUtils {

    private JwtUtils() {
        // Clase utilitaria - constructor privado
    }

    /**
     * Extrae el merchantId del JWT. Primero intenta obtenerlo del claim "merchantId",
     * si no existe, lo deriva del "sub" (userId).
     *
     * @param jwt El token JWT
     * @return El merchantId o null si no se puede determinar
     */
    public static String extractMerchantId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        // Intentar obtener merchantId directamente del claim
        Object merchantIdObj = jwt.getClaims().get("merchantId");
        if (merchantIdObj != null) {
            return String.valueOf(merchantIdObj);
        }

        // Fallback: derivar del sub (userId)
        Object subObj = jwt.getClaims().get("sub");
        if (subObj != null) {
            String sub = String.valueOf(subObj);
            if (!sub.isBlank()) {
                return "mrc_" + sub.substring(0, Math.min(sub.length(), 8));
            }
        }

        return null;
    }

    /**
     * Extrae el userId del JWT desde el claim "sub".
     *
     * @param jwt El token JWT
     * @return El userId o null si no existe
     */
    public static String extractUserId(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        Object subObj = jwt.getClaims().get("sub");
        if (subObj != null) {
            String sub = String.valueOf(subObj);
            return sub.isBlank() ? null : sub;
        }

        return null;
    }

    /**
     * Extrae el email del JWT desde el claim "email".
     *
     * @param jwt El token JWT
     * @return El email o null si no existe
     */
    public static String extractEmail(Jwt jwt) {
        if (jwt == null) {
            return null;
        }

        Object emailObj = jwt.getClaims().get("email");
        if (emailObj != null) {
            String email = String.valueOf(emailObj);
            return email.isBlank() ? null : email;
        }

        return null;
    }
}

