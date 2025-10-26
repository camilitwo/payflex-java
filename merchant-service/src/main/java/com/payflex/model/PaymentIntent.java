package com.payflex.model;

import io.r2dbc.postgresql.codec.Json;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("payment_intents")
public class PaymentIntent implements Persistable<String> {

    @Id
    @Column("id")
    private String id;

    @Transient
    @Builder.Default
    private boolean isNew = true;

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
    private Json metadata; // JSON type

    @Column("client_secret")
    private String clientSecret;

    @Column("last_payment_error")
    private Json lastPaymentError; // JSON type

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    // Helper methods para trabajar con JSON como String
    public String getMetadataAsString() {
        if (metadata == null) {
            return null;
        }
        return new String(metadata.asArray(), StandardCharsets.UTF_8);
    }

    public void setMetadataFromString(String metadataString) {
        if (metadataString == null || metadataString.trim().isEmpty()) {
            this.metadata = null;
        } else {
            this.metadata = Json.of(metadataString.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String getLastPaymentErrorAsString() {
        if (lastPaymentError == null) {
            return null;
        }
        return new String(lastPaymentError.asArray(), StandardCharsets.UTF_8);
    }

    public void setLastPaymentErrorFromString(String errorString) {
        if (errorString == null || errorString.trim().isEmpty()) {
            this.lastPaymentError = null;
        } else {
            this.lastPaymentError = Json.of(errorString.getBytes(StandardCharsets.UTF_8));
        }
    }

    // Métodos de Persistable para controlar INSERT vs UPDATE
    @Override
    public boolean isNew() {
        return isNew || createdAt == null;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}
