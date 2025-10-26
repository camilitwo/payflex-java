package com.payflex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaymentIntentRequest {
    private String status;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethodId;
    private String description;
    private String statementDescriptor;
    private String metadata;
    private String lastPaymentError;
}

