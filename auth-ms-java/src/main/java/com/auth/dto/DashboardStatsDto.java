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
public class DashboardStatsDto {

    private TransactionStats transactions;
    private IncomeStats income;
    private GrowthStats growth;
    private OutflowStats egresos; // nuevo bloque expuesto al front

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionStats {
        private Long count;
        private Double percentageChange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IncomeStats {
        private BigDecimal amount;
        private String currency;
        private Double percentageChange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrowthStats {
        private Double percentage;
        private Double percentageChange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutflowStats {
        private BigDecimal amount;
        private String currency;
        private Double percentageChange;
    }
}
