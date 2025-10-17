package com.payflex.dto;

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
public class MerchantResponse {

    private String merchantId;
    private String businessName;
    private String legalName;
    private String taxId;
    private String email;
    private String phone;
    private String website;
    private String status;
    private Boolean onboardingCompleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Balance information
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private String currency;
}
