package com.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentIntentRequest {
    private String id;
    private String merchantId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String captureMethod;
    private String confirmationMethod;
    private String description;
    private String statementDescriptor;
    private String metadata; // JSON string
}

