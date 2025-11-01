package com.payflex.web;

import com.payflex.client.MerchantServiceClient;
import com.payflex.dto.CreatePaymentIntentRequest;
import com.payflex.dto.PaymentIntentResponse;
import com.payflex.event.producer.PaymentEventProducer;
import com.payflex.security.MerchantAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentsController {
  private static final Logger log = LoggerFactory.getLogger(PaymentsController.class);

  private final MerchantAccess merchantAccess;
  private final MerchantServiceClient merchantServiceClient;
  private final PaymentEventProducer paymentEventProducer;

  public PaymentsController(MerchantAccess merchantAccess, MerchantServiceClient merchantServiceClient, PaymentEventProducer paymentEventProducer) {
    this.merchantAccess = merchantAccess;
    this.merchantServiceClient = merchantServiceClient;
      this.paymentEventProducer = paymentEventProducer;
  }

  @PostMapping(value="/intents", consumes=MediaType.APPLICATION_JSON_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
  public Mono<Map<String,String>> createIntent(@RequestBody Map<String,Object> body, Authentication auth){
    log.info("[createIntent] raw body={}", body);
    log.info("[createIntent] Authentication: {}", auth);

    Object merchantIdObj = body.get("merchantId");
    Object amountObj = body.get("amount");
    Object currencyObj = body.getOrDefault("currency", "CLP");

    if (merchantIdObj == null || merchantIdObj.toString().isBlank()) {
      return Mono.just(Map.of(
          "error", "merchantId is required",
          "status", "400"
      ));
    }
    if (amountObj == null || amountObj.toString().isBlank()) {
      return Mono.just(Map.of(
          "error", "amount is required",
          "status", "400"
      ));
    }

    String merchantId = merchantIdObj.toString();

    // Solo verificar acceso si hay autenticación presente
    if (auth != null) {
      merchantAccess.ensure(auth, merchantId);
    } else {
      log.warn("[createIntent] No authentication present - skipping merchant access check");
    }

    String currency = currencyObj == null || currencyObj.toString().isBlank() ? "CLP" : currencyObj.toString();
    String paymentIntentId = "pi_" + UUID.randomUUID();

    // Crear el payment intent en la base de datos a través del merchant-service
    return Mono.fromCallable(() -> {
      CreatePaymentIntentRequest request = new CreatePaymentIntentRequest();
      request.setId(paymentIntentId);
      request.setMerchantId(merchantId);
      request.setAmount(new BigDecimal(amountObj.toString()));
      request.setCurrency(currency);
      request.setStatus("requires_payment_method");
      request.setCaptureMethod("automatic");
      request.setConfirmationMethod("automatic");

      // Agregar campos opcionales si existen
      if (body.containsKey("customerId")) {
        request.setCustomerId(body.get("customerId").toString());
      }
      if (body.containsKey("description")) {
        request.setDescription(body.get("description").toString());
      }
      if (body.containsKey("statementDescriptor")) {
        request.setStatementDescriptor(body.get("statementDescriptor").toString());
      }

      log.info("[createIntent] Calling merchant-service to save payment intent: {}", request.getId());

      //PaymentIntentResponse response = merchantServiceClient.createPaymentIntent(request);
      paymentEventProducer.publishPaymentApproved(request);

      return Map.of(
          "status", "succes",
            "paymentIntentId", paymentIntentId,
              "eventPublished", "PAYMENT_APPROVED"
      );
    }).onErrorResume(error -> {
      log.error("[createIntent] Error creating payment intent", error);
      return Mono.just(Map.of(
          "error", "Failed to create payment intent: " + error.getMessage(),
          "status", "500"
      ));
    });
  }
}
