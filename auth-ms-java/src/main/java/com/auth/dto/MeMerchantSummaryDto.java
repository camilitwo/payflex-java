package com.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeMerchantSummaryDto {
    private MeMerchantDto merchant;
    private MeMerchantConfigDto config;
    private List<MeMerchantUserDto> users;
}

