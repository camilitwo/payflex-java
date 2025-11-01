package com.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentDto {
    private String id;
    private String merchantId;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String paymentMethodId;
    private String captureMethod;
    private String confirmationMethod;
    private String description;
    private String statementDescriptor;
    private String metadata;
    private String clientSecret;
    private String lastPaymentError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


