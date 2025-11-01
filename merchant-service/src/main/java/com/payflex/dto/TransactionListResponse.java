package com.payflex.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionListResponse {
    private List<PaymentIntentResponse> transactions;
    private long totalCount;
    private int page;
    private int pageSize;
    private boolean hasMore;
}

