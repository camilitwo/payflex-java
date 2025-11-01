package com.payflex.service;

import com.payflex.dto.DashboardStatsResponse;
import com.payflex.repository.PaymentIntentRepository;
import com.payflex.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardStatsService {

    private final PaymentIntentRepository paymentIntentRepository;
    private final RefundRepository refundRepository; // nuevo repo para egresos

    public Mono<DashboardStatsResponse> getMerchantDashboardStats(String merchantId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentPeriodStart = now.minus(30, ChronoUnit.DAYS);
        LocalDateTime previousPeriodStart = currentPeriodStart.minus(30, ChronoUnit.DAYS);
        LocalDateTime previousPeriodEnd = currentPeriodStart;

        String successStatus = "succeeded";

        // Período actual
        Mono<Long> currentTransactionCount = paymentIntentRepository
                .countByMerchantIdAndStatusAndCreatedAtBetween(merchantId, successStatus, currentPeriodStart, now)
                .defaultIfEmpty(0L);

        Mono<Long> currentIncomeAmount = paymentIntentRepository
                .sumAmountByMerchantIdAndStatusAndCreatedAtBetween(merchantId, successStatus, currentPeriodStart, now)
                .defaultIfEmpty(0L);

        Mono<Long> currentOutflowsAmount = refundRepository
                .sumAmountByMerchantIdAndStatusAndCreatedAtBetween(merchantId, successStatus, currentPeriodStart, now)
                .defaultIfEmpty(0L);

        // Período anterior
        Mono<Long> previousTransactionCount = paymentIntentRepository
                .countByMerchantIdAndStatusAndCreatedAtBetween(merchantId, successStatus, previousPeriodStart, previousPeriodEnd)
                .defaultIfEmpty(0L);

        Mono<Long> previousIncomeAmount = paymentIntentRepository
                .sumAmountByMerchantIdAndStatusAndCreatedAtBetween(merchantId, successStatus, previousPeriodStart, previousPeriodEnd)
                .defaultIfEmpty(0L);

        Mono<Long> previousOutflowsAmount = refundRepository
                .sumAmountByMerchantIdAndStatusAndCreatedAtBetween(merchantId, successStatus, previousPeriodStart, previousPeriodEnd)
                .defaultIfEmpty(0L);

        // Moneda predominante
        Mono<String> currency = paymentIntentRepository
                .findMostCommonCurrencyByMerchantIdAndStatusAndCreatedAtBetween(merchantId, successStatus, currentPeriodStart, now)
                .defaultIfEmpty("CLP");

        return Mono.zip(
                        currentTransactionCount,
                        previousTransactionCount,
                        currentIncomeAmount,
                        previousIncomeAmount,
                        currentOutflowsAmount,
                        previousOutflowsAmount,
                        currency
                )
                .map(tuple -> {
                    Long currentTxCount = tuple.getT1();
                    Long previousTxCount = tuple.getT2();
                    Long currentIncome = tuple.getT3();
                    Long previousIncome = tuple.getT4();
                    Long currentOutflows = tuple.getT5();
                    Long previousOutflows = tuple.getT6();
                    String merchantCurrency = tuple.getT7();

                    // Porcentajes
                    Double transactionPercentageChange = calculatePercentageChange(previousTxCount, currentTxCount);
                    Double incomePercentageChange = calculatePercentageChange(previousIncome, currentIncome);
                    Double outflowsPercentageChange = calculatePercentageChange(previousOutflows, currentOutflows);

                    // Tasa de crecimiento general (promedio ponderado)
                    Double growthPercentage = calculateGrowthRate(transactionPercentageChange, incomePercentageChange);
                    Double growthChange = calculatePercentageChange(
                            previousIncome.doubleValue(),
                            currentIncome.doubleValue()
                    );

                    return DashboardStatsResponse.builder()
                            .transactions(DashboardStatsResponse.TransactionStats.builder()
                                    .count(currentTxCount)
                                    .percentageChange(transactionPercentageChange)
                                    .build())
                            .income(DashboardStatsResponse.IncomeStats.builder()
                                    .amount(formatAmount(currentIncome, merchantCurrency))
                                    .currency(merchantCurrency)
                                    .percentageChange(incomePercentageChange)
                                    .build())
                            .egresos(DashboardStatsResponse.OutflowStats.builder()
                                    .amount(formatAmount(currentOutflows, merchantCurrency))
                                    .currency(merchantCurrency)
                                    .percentageChange(outflowsPercentageChange)
                                    .build())
                            .growth(DashboardStatsResponse.GrowthStats.builder()
                                    .percentage(growthPercentage)
                                    .percentageChange(growthChange)
                                    .build())
                            .build();
                });
    }

    private Double calculatePercentageChange(Long previousValue, Long currentValue) {
        if (previousValue == 0) {
            return currentValue > 0 ? 100.0 : 0.0;
        }
        double change = ((currentValue.doubleValue() - previousValue.doubleValue()) / previousValue.doubleValue()) * 100;
        return Math.round(change * 100.0) / 100.0; // Redondear a 2 decimales
    }

    private Double calculatePercentageChange(Double previousValue, Double currentValue) {
        if (previousValue == 0) {
            return currentValue > 0 ? 100.0 : 0.0;
        }
        double change = ((currentValue - previousValue) / previousValue) * 100;
        return Math.round(change * 100.0) / 100.0;
    }

    private Double calculateGrowthRate(Double transactionChange, Double incomeChange) {
        // Promedio ponderado: más peso a los ingresos (60%) que a las transacciones (40%)
        double growthRate = (transactionChange * 0.4) + (incomeChange * 0.6);
        return Math.round(growthRate * 100.0) / 100.0;
    }

    /**
     * Formatea el monto según la moneda.
     * Monedas sin decimales (zero-decimal currencies): JPY, KRW, CLP, etc. no se dividen por 100
     * Monedas normales: USD, EUR, GBP, etc. se dividen por 100
     */
    private BigDecimal formatAmount(Long amountInCents, String currency) {
        if (amountInCents == null) {
            return BigDecimal.ZERO;
        }

        // Monedas que no usan decimales (ya vienen en unidades completas, no en centavos)
        String[] zeroDecimalCurrencies = {"JPY", "KRW", "CLP", "VND", "ISK", "BIF", "DJF", "GNF",
                                          "KMF", "XAF", "XOF", "XPF", "RWF", "UGX", "VUV", "PYG"};

        boolean isZeroDecimal = false;
        for (String curr : zeroDecimalCurrencies) {
            if (curr.equalsIgnoreCase(currency)) {
                isZeroDecimal = true;
                break;
            }
        }

        if (isZeroDecimal) {
            // Para monedas sin decimales, el monto ya está en la unidad correcta
            return BigDecimal.valueOf(amountInCents);
        } else {
            // Para monedas con decimales, dividir por 100 para convertir centavos a unidades
            return BigDecimal.valueOf(amountInCents)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
    }
}
