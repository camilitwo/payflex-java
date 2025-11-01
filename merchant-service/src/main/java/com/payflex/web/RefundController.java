package com.payflex.web;


import com.payflex.dto.CreateRefundRequest;
import com.payflex.dto.RefundResponse;
import com.payflex.service.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/refunds")
public class RefundController {
    private static final Logger log = LoggerFactory.getLogger(RefundController.class);

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RefundResponse> createRefund(@RequestBody CreateRefundRequest request) {
        log.info("[createRefund] Received request: {}", request);
        return refundService.createRefund(request);
    }

    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<RefundResponse> getRefund(@PathVariable String id) {
        log.info("[getRefund] Fetching refund: {}", id);
        return refundService.getRefund(id);
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<RefundResponse> getRefunds(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String paymentIntentId,
            @RequestParam(required = false) String status) {
        log.info("[getRefunds] Fetching refunds - merchantId: {}, paymentIntentId: {}, status: {}",
                merchantId, paymentIntentId, status);

        if (paymentIntentId != null) {
            return refundService.getRefundsByPaymentIntent(paymentIntentId);
        } else if (merchantId != null && status != null) {
            return refundService.getRefundsByMerchantAndStatus(merchantId, status);
        } else if (merchantId != null) {
            return refundService.getRefundsByMerchant(merchantId);
        } else {
            return Flux.error(new IllegalArgumentException("merchantId or paymentIntentId is required"));
        }
    }

    @PostMapping(value = "/{id}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Void> cancelRefund(@PathVariable String id) {
        log.info("[cancelRefund] Canceling refund: {}", id);
        return refundService.cancelRefund(id);
    }
}

