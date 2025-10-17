package com.payflex.merchant.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("merchant_payment_configs")
public class MerchantPaymentConfig {

    @Id
    @Column("id")
    private Long id;

    @Column("merchant_id")
    private String merchantId;

    @Column("api_key_hash")
    private String apiKeyHash;

    @Column("webhook_url")
    private String webhookUrl;

    @Column("webhook_secret")
    private String webhookSecret;

    @Column("default_currency")
    private String defaultCurrency;

    @Column("payment_methods_enabled")
    private String paymentMethodsEnabled; // JSON as String

    @Column("auto_capture")
    private Boolean autoCapture;

    @Column("statement_descriptor")
    private String statementDescriptor;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
