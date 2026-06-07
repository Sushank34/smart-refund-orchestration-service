package com.refund.service;

import com.refund.domain.RiskLevel;
import java.util.List;

/** Outcome of scoring a single refund, including the signals that fired. */
public record RiskAssessment(int score, RiskLevel level, List<String> reasons) {
}
