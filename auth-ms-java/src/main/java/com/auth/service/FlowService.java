package com.auth.service;

import com.auth.dto.PaymentDTO;
import reactor.core.publisher.Mono;

public interface FlowService {
    Mono<String> createPayment(String merchantId, String email, Long amount, String subject);

    Mono<PaymentDTO> handleConfirmation(String token);

    Mono<PaymentDTO> getPayment(String commerceOrder);
}
