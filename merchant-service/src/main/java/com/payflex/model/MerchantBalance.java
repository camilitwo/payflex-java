package com.payflex.model;

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
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("merchant_balances")
public class MerchantBalance implements Persistable<String> {

    @Id
    @Column("id")
    private String id;

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Column("merchant_id")
    private String merchantId;

    @Column("available_balance")
    private BigDecimal availableBalance;

    @Column("pending_balance")
    private BigDecimal pendingBalance;

    @Column("total_withdrawn")
    private BigDecimal totalWithdrawn;

    @Column("currency")
    private String currency;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Override
    public boolean isNew() {
        return isNew;
    }

    public void markAsNotNew() {
        this.isNew = false;
    }
}
