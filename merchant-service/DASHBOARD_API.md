# Dashboard Metrics API

## Endpoint

`GET /merchants/{merchantId}/dashboard/metrics`

## Description

This endpoint provides dashboard metrics for a merchant including transaction counts, revenue, and success rate growth metrics.

## Request

### Path Parameters
- `merchantId` (string, required): The unique identifier of the merchant

### Example Request
```
GET /merchants/mrc_123456789/dashboard/metrics
```

## Response

### Response Body
```json
{
  "transacciones": {
    "valor": 1234,
    "cambioPorcentual": 12.5
  },
  "ingresos": {
    "valor": 45678.50,
    "cambioPorcentual": 8.2
  },
  "crecimiento": {
    "valor": 85.5,
    "variacion": 5.3
  }
}
```

### Response Fields

#### transacciones (MetricDto)
- `valor` (BigDecimal): Total number of payment intents in the current period (last 30 days)
- `cambioPorcentual` (Double): Percentage change compared to the previous period

#### ingresos (MetricDto)
- `valor` (BigDecimal): Total revenue from successful transactions in the current period
- `cambioPorcentual` (Double): Percentage change in revenue compared to the previous period

#### crecimiento (GrowthMetricDto)
- `valor` (Double): Success rate percentage (successful transactions / total transactions * 100)
- `variacion` (Double): Percentage point change in success rate compared to the previous period

## Calculation Logic

### Time Periods
- **Current Period**: Last 30 days from now
- **Previous Period**: 30 days before the current period (days 31-60 ago)

### Metrics Calculation

1. **Transactions**: Count of all payment intents created in the period
2. **Revenue**: Sum of amounts from payment intents with status "succeeded"
3. **Growth (Success Rate)**: Percentage of successful transactions out of total transactions

### Percentage Change
- Calculated as: `((current - previous) / previous) * 100`
- If previous value is 0, returns 100% if current > 0, otherwise 0%
- If current value is 0 and previous > 0, returns -100%

## Example Use Case

This endpoint can be used to populate a merchant dashboard showing:
- Transaction volume trends
- Revenue performance
- Transaction success rate improvements

The metrics help merchants understand their payment processing performance and identify trends over time.
