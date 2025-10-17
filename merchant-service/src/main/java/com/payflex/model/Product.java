package com.payflex.merchant.model;

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
@Table("products")
public class Product {

    @Id
    @Column("id")
    private String id;

    @Column("merchant_id")
    private String merchantId;

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("price")
    private BigDecimal price;

    @Column("currency")
    private String currency;

    @Column("sku")
    private String sku;

    @Column("stock_quantity")
    private Integer stockQuantity;

    @Column("status")
    private String status; // active, inactive, archived

    @Column("images")
    private String images; // JSON as String

    @Column("metadata")
    private String metadata; // JSON as String

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
