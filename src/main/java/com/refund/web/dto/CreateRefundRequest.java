package com.refund.web.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Request body for creating a refund. Amount is in major units (e.g. euros). */
public record CreateRefundRequest(
        @NotNull(message = "amount is required") BigDecimal amount,
        String reason) {
}
