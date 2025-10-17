package com.payflex.merchant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMerchantRequest {
    private String merchantId;
    private String userId;
    private String businessName;
    private String legalName;
    private String taxId;
    private String email;
    private String phone;
    private String website;
    private String status;
    private Boolean onboardingCompleted;
    private String role;
    private String passwordHash;

    // Aplica valores por defecto si faltan y retorna la misma instancia (fluido)
    public CreateMerchantRequest applyFallbacks(String merchantId, String businessName, String email){
        if (this.merchantId == null || this.merchantId.isBlank()) {
            this.merchantId = merchantId;
        }
        if (this.businessName == null || this.businessName.isBlank()) {
            this.businessName = businessName;
        }
        if (this.email == null || this.email.isBlank()) {
            this.email = email;
        }
        if (this.legalName == null || this.legalName.isBlank()) {
            this.legalName = this.businessName;
        }
        if (this.taxId == null || this.taxId.isBlank()) {
            this.taxId = "PENDING-" + this.merchantId;
        }
        if (this.status == null || this.status.isBlank()) {
            this.status = "active";
        }
        if (this.onboardingCompleted == null) {
            this.onboardingCompleted = false;
        }
        return this;
    }
}
