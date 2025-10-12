package com.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeMerchantConfigDto {
    private String merchantId;
    private String defaultCurrency;
    private String paymentMethodsEnabled; // JSON string of enabled methods
    private Boolean autoCapture;
    private String webhookUrl;
    private String statementDescriptor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

