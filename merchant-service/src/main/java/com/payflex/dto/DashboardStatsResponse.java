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
public class DashboardStatsResponse {

    private TransactionStats transactions;
    private IncomeStats income;
    private GrowthStats growth;
    private OutflowStats egresos; // Renombrado a 'egresos' para el front

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

    // Datos de egresos (sumatoria de refunds succeeded)
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
