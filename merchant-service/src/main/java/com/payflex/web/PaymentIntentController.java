package com.payflex.web;

import com.payflex.dto.CreatePaymentIntentRequest;
import com.payflex.dto.CreateRefundRequest;
import com.payflex.dto.PaymentIntentResponse;
import com.payflex.dto.RefundResponse;
import com.payflex.dto.TransactionListResponse;
import com.payflex.dto.UpdatePaymentIntentRequest;
import com.payflex.service.PaymentIntentService;
import com.payflex.service.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/payment-intents")
public class PaymentIntentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentIntentController.class);

    private final PaymentIntentService paymentIntentService;
    private final RefundService refundService;

    public PaymentIntentController(PaymentIntentService paymentIntentService, RefundService refundService) {
        this.paymentIntentService = paymentIntentService;
        this.refundService = refundService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PaymentIntentResponse> createPaymentIntent(@RequestBody CreatePaymentIntentRequest request) {
        log.info("[createPaymentIntent] Received request: {}", request);
        return paymentIntentService.createPaymentIntent(request);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<PaymentIntentResponse> getPaymentIntent(@PathVariable String id) {
        log.info("[getPaymentIntent] Fetching payment intent: {}", id);
        return paymentIntentService.getPaymentIntent(id);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<PaymentIntentResponse> getPaymentIntents(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String status) {
        log.info("[getPaymentIntents] Fetching payment intents - merchantId: {}, status: {}", merchantId, status);

        if (merchantId != null && status != null) {
            return paymentIntentService.getPaymentIntentsByMerchantAndStatus(merchantId, status);
        } else if (merchantId != null) {
            return paymentIntentService.getPaymentIntentsByMerchant(merchantId);
        } else {
            return Flux.error(new IllegalArgumentException("merchantId is required"));
        }
    }

    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<PaymentIntentResponse> updatePaymentIntent(
            @PathVariable String id,
            @RequestBody UpdatePaymentIntentRequest request) {
        log.info("[updatePaymentIntent] Updating payment intent: {} with data: {}", id, request);
        return paymentIntentService.updatePaymentIntent(id, request);
    }

    @PostMapping(value = "/{id}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> cancelPaymentIntent(@PathVariable String id) {
        log.info("[cancelPaymentIntent] Canceling payment intent: {}", id);
        return paymentIntentService.cancelPaymentIntent(id);
    }

    // Endpoint específico para obtener transacciones paginadas para el dashboard del frontend
    @GetMapping(value = "/merchant/{merchantId}/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<TransactionListResponse> getTransactionsForDashboard(
            @PathVariable String merchantId,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {

        log.info("[getTransactionsForDashboard] Fetching transactions for merchant: {}, status: {}, page: {}, pageSize: {}",
                merchantId, status, page, pageSize);

        return paymentIntentService.getTransactionsForDashboard(merchantId, status, page, pageSize);
    }

    // Endpoint para crear un retiro de dinero desde un payment intent
    @PostMapping(value = "/{id}/refunds", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<RefundResponse>> createWithdrawalForPaymentIntent(
            @PathVariable String id,
            @RequestBody CreateRefundRequest request) {
        log.info("[createWithdrawalForPaymentIntent] Creating withdrawal for payment intent: {} with data: {}", id, request);

        // Asegurarse de que el paymentIntentId esté en el request
        request.setPaymentIntentId(id);

        return refundService.createRefund(request)
                .map(response -> ResponseEntity.status(HttpStatus.CREATED).body(response));
    }

    // Endpoint para listar retiros de un payment intent
    @GetMapping(value = "/{id}/refunds", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<RefundResponse> getWithdrawalsForPaymentIntent(@PathVariable String id) {
        log.info("[getWithdrawalsForPaymentIntent] Fetching withdrawals for payment intent: {}", id);
        return refundService.getRefundsByPaymentIntent(id);
    }
}
