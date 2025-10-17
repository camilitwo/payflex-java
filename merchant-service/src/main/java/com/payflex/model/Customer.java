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
@Table("customers")
public class Customer {

    @Id
    @Column("id")
    private String id;

    @Column("merchant_id")
    private String merchantId;

    @Column("email")
    private String email;

    @Column("name")
    private String name;

    @Column("phone")
    private String phone;

    @Column("metadata")
    private String metadata; // JSON as String

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
