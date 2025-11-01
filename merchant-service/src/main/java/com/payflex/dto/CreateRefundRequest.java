package com.payflex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRefundRequest {
    private String paymentIntentId; // ID del payment intent del cual se retira el dinero
    private BigDecimal amount; // Monto a retirar (obligatorio)
    private String reason; // withdrawal (retiro), payout (pago), transfer (transferencia)
    private Map<String, Object> metadata;
}

