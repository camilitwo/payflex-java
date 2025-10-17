package com.payflex.model;

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
@Table("merchant_balances")
public class MerchantBalance {

    @Id
    private Long id;

    @Column("merchant_id")
    private String merchantId;

    @Column("available_balance")
    private BigDecimal availableBalance;

    @Column("pending_balance")
    private BigDecimal pendingBalance;

    private String currency;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
