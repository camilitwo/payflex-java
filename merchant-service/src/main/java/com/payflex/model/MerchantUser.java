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
@Table("merchant_users")
public class MerchantUser {

    @Id
    @Column("user_id")
    private String userId; // PK, ID del usuario de PocketBase

    @Column("merchant_id")
    private String merchantId;

    @Column("email")
    private String email;

    @Column("password_hash")
    private String passwordHash;

    @Column("name")
    private String name;

    @Column("phone")
    private String phone;

    @Column("role")
    private String role; // MERCHANT_ADMIN, MERCHANT_OPERATOR, MERCHANT_VIEWER

    @Column("status")
    private String status; // active, inactive, suspended

    @Column("email_verified")
    private Boolean emailVerified;

    @Column("last_login_at")
    private LocalDateTime lastLoginAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
