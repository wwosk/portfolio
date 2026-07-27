package org.example.mypaintings.dashboard.artist.dto;

import lombok.Builder;
import org.example.mypaintings.sale.financial.Money;

import java.math.BigDecimal;
import java.time.Instant;

@Builder
public record SaleDashboardResponse(
    Long paintingId,
    String paintingTitle,
    String paintingUrl,
    Money artistEarnings,
    Money clientPaid,
    Instant saleDate
){}
