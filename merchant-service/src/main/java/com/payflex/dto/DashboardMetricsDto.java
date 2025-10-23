package com.payflex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMetricsDto {
    
    private MetricDto transacciones;
    private MetricDto ingresos;
    private GrowthMetricDto crecimiento;
}
