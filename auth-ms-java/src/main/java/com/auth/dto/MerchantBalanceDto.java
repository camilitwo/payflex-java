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
public class MerchantBalanceDto {
    private String id;
    private String merchantId;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal totalWithdrawn;
    private String currency;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}


