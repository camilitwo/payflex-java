package com.payflex.service;

import com.payflex.dto.DashboardMetricsDto;
import com.payflex.dto.GrowthMetricDto;
import com.payflex.dto.MetricDto;
import com.payflex.model.PaymentIntent;
import com.payflex.repository.PaymentIntentRepository;
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
public class DashboardService {

    private final PaymentIntentRepository paymentIntentRepository;

    public Mono<DashboardMetricsDto> getDashboardMetrics(String merchantId) {
        log.info("Calculating dashboard metrics for merchant: {}", merchantId);
        
        // Define time periods
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentPeriodStart = now.minus(30, ChronoUnit.DAYS);
        LocalDateTime previousPeriodStart = currentPeriodStart.minus(30, ChronoUnit.DAYS);
        LocalDateTime previousPeriodEnd = currentPeriodStart;
        
        // Get current period metrics
        Mono<Long> currentTransactionCount = paymentIntentRepository
            .countByMerchantIdAndCreatedAtBetween(merchantId, currentPeriodStart, now)
            .defaultIfEmpty(0L);
            
        Mono<Long> currentSuccessfulCount = paymentIntentRepository
            .countByMerchantIdAndStatusAndCreatedAtBetween(merchantId, "succeeded", currentPeriodStart, now)
            .defaultIfEmpty(0L);
            
        Mono<BigDecimal> currentRevenue = paymentIntentRepository
            .findByMerchantIdAndCreatedAtBetween(merchantId, currentPeriodStart, now)
            .filter(pi -> "succeeded".equals(pi.getStatus()))
            .map(PaymentIntent::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Get previous period metrics
        Mono<Long> previousTransactionCount = paymentIntentRepository
            .countByMerchantIdAndCreatedAtBetween(merchantId, previousPeriodStart, previousPeriodEnd)
            .defaultIfEmpty(0L);
            
        Mono<Long> previousSuccessfulCount = paymentIntentRepository
            .countByMerchantIdAndStatusAndCreatedAtBetween(merchantId, "succeeded", previousPeriodStart, previousPeriodEnd)
            .defaultIfEmpty(0L);
            
        Mono<BigDecimal> previousRevenue = paymentIntentRepository
            .findByMerchantIdAndCreatedAtBetween(merchantId, previousPeriodStart, previousPeriodEnd)
            .filter(pi -> "succeeded".equals(pi.getStatus()))
            .map(PaymentIntent::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Combine all metrics
        return Mono.zip(
            currentTransactionCount,
            previousTransactionCount,
            currentRevenue,
            previousRevenue,
            currentSuccessfulCount,
            previousSuccessfulCount
        ).map(tuple -> {
            Long currentTxCount = tuple.getT1();
            Long previousTxCount = tuple.getT2();
            BigDecimal currentRev = tuple.getT3();
            BigDecimal previousRev = tuple.getT4();
            Long currentSuccess = tuple.getT5();
            Long previousSuccess = tuple.getT6();
            
            // Calculate transaction metrics
            Double transactionChange = calculatePercentageChange(
                currentTxCount.doubleValue(), 
                previousTxCount.doubleValue()
            );
            
            MetricDto transacciones = MetricDto.builder()
                .valor(BigDecimal.valueOf(currentTxCount))
                .cambioPorcentual(transactionChange)
                .build();
            
            // Calculate revenue metrics
            Double revenueChange = calculatePercentageChange(
                currentRev.doubleValue(), 
                previousRev.doubleValue()
            );
            
            MetricDto ingresos = MetricDto.builder()
                .valor(currentRev.setScale(2, RoundingMode.HALF_UP))
                .cambioPorcentual(revenueChange)
                .build();
            
            // Calculate growth metrics (success rate)
            Double currentSuccessRate = currentTxCount > 0 
                ? (currentSuccess.doubleValue() / currentTxCount.doubleValue()) * 100.0
                : 0.0;
            Double previousSuccessRate = previousTxCount > 0
                ? (previousSuccess.doubleValue() / previousTxCount.doubleValue()) * 100.0
                : 0.0;
            
            Double growthVariation = calculatePercentageChange(currentSuccessRate, previousSuccessRate);
            
            GrowthMetricDto crecimiento = GrowthMetricDto.builder()
                .valor(round(currentSuccessRate, 2))
                .variacion(growthVariation)
                .build();
            
            log.info("Dashboard metrics calculated - Transactions: {} ({}%), Revenue: {} ({}%), Growth: {}% ({}%)",
                currentTxCount, transactionChange, currentRev, revenueChange, currentSuccessRate, growthVariation);
            
            return DashboardMetricsDto.builder()
                .transacciones(transacciones)
                .ingresos(ingresos)
                .crecimiento(crecimiento)
                .build();
        });
    }
    
    private Double calculatePercentageChange(Double current, Double previous) {
        if (previous == null || previous == 0.0) {
            return current != null && current > 0.0 ? 100.0 : 0.0;
        }
        if (current == null) {
            return -100.0;
        }
        double change = ((current - previous) / previous) * 100.0;
        return round(change, 2);
    }
    
    private Double round(Double value, int places) {
        if (value == null) {
            return 0.0;
        }
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }
}
