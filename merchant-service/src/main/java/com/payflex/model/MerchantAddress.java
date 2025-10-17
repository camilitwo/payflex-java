package com.payflex.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("merchant_addresses")
public class MerchantAddress {
    @Id
    @Column("id")
    private Long id; // SERIAL PK

    @Column("merchant_id")
    private String merchantId; // FK merchants.id

    @Column("address_type")
    private String addressType; // billing | shipping | legal

    @Column("street_address")
    private String streetAddress;

    @Column("city")
    private String city;

    @Column("state")
    private String state;

    @Column("postal_code")
    private String postalCode;

    @Column("country")
    private String country; // ISO2, default CL

    @Column("is_primary")
    private Boolean isPrimary;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}

