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
@Table("payment_methods")
public class PaymentMethod {

    @Id
    @Column("id")
    private String id;

    @Column("customer_id")
    private String customerId;

    @Column("merchant_id")
    private String merchantId;

    @Column("type")
    private String type; // card, transfer, wallet

    @Column("card_brand")
    private String cardBrand;

    @Column("card_last4")
    private String cardLast4;

    @Column("card_exp_month")
    private Integer cardExpMonth;

    @Column("card_exp_year")
    private Integer cardExpYear;

    @Column("card_fingerprint")
    private String cardFingerprint;

    @Column("is_default")
    private Boolean isDefault;

    @Column("metadata")
    private String metadata; // JSON as String

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
