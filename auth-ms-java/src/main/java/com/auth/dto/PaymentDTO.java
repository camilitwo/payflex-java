package com.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentDTO {

    private String commerceOrder;
    private String email;
    private Long amount;
    private String subject;
    private PaymentStatus status;
}
