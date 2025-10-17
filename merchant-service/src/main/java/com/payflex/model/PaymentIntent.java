package com.payflex.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("payment_intents")
public class PaymentIntent {

    @Id
    @Column("id")
    private String id;

    @Column("merchant_id")
    private String merchantId;

    @Column("customer_id")
    private String customerId;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("status")
    private String status; // requires_payment_method, requires_confirmation, requires_action, processing, requires_capture, canceled, succeeded

    @Column("payment_method_id")
    private String paymentMethodId;

    @Column("capture_method")
    private String captureMethod;

    @Column("confirmation_method")
    private String confirmationMethod;

    @Column("description")
    private String description;

    @Column("statement_descriptor")
    private String statementDescriptor;

    @Column("metadata")
    private String metadata; // JSON as String

    @Column("client_secret")
    private String clientSecret;

    @Column("last_payment_error")
    private String lastPaymentError; // JSON as String

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
