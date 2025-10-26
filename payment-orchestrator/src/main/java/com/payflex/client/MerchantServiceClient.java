package com.payflex.client;

import com.payflex.dto.CreatePaymentIntentRequest;
import com.payflex.dto.PaymentIntentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "merchant-service")
public interface MerchantServiceClient {

    @PostMapping(value = "/api/payment-intents", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    PaymentIntentResponse createPaymentIntent(@RequestBody CreatePaymentIntentRequest request);

    @GetMapping(value = "/api/payment-intents/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    PaymentIntentResponse getPaymentIntent(@PathVariable("id") String id);

    @GetMapping(value = "/api/payment-intents", produces = MediaType.APPLICATION_JSON_VALUE)
    List<PaymentIntentResponse> getPaymentIntents(
            @RequestParam(required = false) String merchantId,
            @RequestParam(required = false) String status
    );
}

