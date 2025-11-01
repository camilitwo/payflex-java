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
@Table("refunds")
public class Refund implements Persistable<String> {

    @Id
    @Column("id")
    private String id;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Column("charge_id")
    private String chargeId;

    @Column("payment_intent_id")
    private String paymentIntentId;

    @Column("merchant_id")
    private String merchantId;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("status")
    private String status; // pending, succeeded, failed, canceled

    @Column("reason")
    private String reason; // withdrawal (retiro de dinero del merchant)

    @Column("metadata")
    private Json metadata;

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

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markAsNotNew() {
        this.isNew = false;
    }
}

