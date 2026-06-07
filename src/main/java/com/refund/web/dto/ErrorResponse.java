package com.refund.web.dto;

/** Consistent error envelope returned for every failure. */
public record ErrorResponse(int status, String code, String error) {
}
