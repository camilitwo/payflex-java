package com.auth.service;

import com.auth.dto.PaymentDTO;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public interface FlowService {
    String createPayment(String merchantId, @Email String email, @Min(1) Long amount, @NotBlank String subject);

    PaymentDTO handleConfirmation(String token) throws Exception;

    PaymentDTO getPayment(String commerceOrder);
}
