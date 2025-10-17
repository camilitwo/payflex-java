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
@Table("merchants")
public class Merchant {

    @Id
    @Column("id")
    private String id; // acts as merchantId (e.g. mrc_xxx)

    @Column("business_name")
    private String businessName;

    @Column("legal_name")
    private String legalName;

    @Column("tax_id")
    private String taxId;

    @Column("email")
    private String email;

    @Column("phone")
    private String phone;

    @Column("website")
    private String website;

    @Column("status")
    private String status; // active, inactive, suspended, pending

    @Column("onboarding_completed")
    private Boolean onboardingCompleted;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    // Elimino los campos que no están en el modelo SQL
    // businessType, address, city, webhookUrl, isActive, country
    // El modelo queda solo con los campos presentes en el SQL
}
