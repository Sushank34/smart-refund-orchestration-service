package com.refund.service;

import com.refund.exception.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Converts between major units (e.g. euros) exposed by the API and the integer cents stored internally. */
public final class Money {

    private Money() {
    }

    public static long toCents(BigDecimal majorUnits) {
        if (majorUnits == null) {
            throw ApiException.unprocessable("INVALID_AMOUNT", "Amount is required.");
        }
        return majorUnits.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static BigDecimal toMajorUnits(long cents) {
        return BigDecimal.valueOf(cents, 2);
    }
}
