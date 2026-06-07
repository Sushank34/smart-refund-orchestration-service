package com.refund.web;

import com.refund.domain.Refund;
import com.refund.domain.RiskLevel;
import com.refund.service.ApprovalService;
import com.refund.service.Money;
import com.refund.service.RefundService;
import com.refund.web.dto.CreateRefundRequest;
import com.refund.web.dto.RefundResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RefundController {

    private final RefundService refundService;
    private final ApprovalService approvalService;

    public RefundController(RefundService refundService, ApprovalService approvalService) {
        this.refundService = refundService;
        this.approvalService = approvalService;
    }

    /** Create a refund against a payment. Pass an Idempotency-Key header to make retries safe. */
    @PostMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<RefundResponse> create(
            @PathVariable String paymentId,
            @Valid @RequestBody CreateRefundRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        Refund refund = refundService.createRefund(
                paymentId, Money.toCents(request.amount()), request.reason(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(RefundResponse.from(refund));
    }

    @GetMapping("/refunds")
    public List<RefundResponse> list(@RequestParam(value = "riskLevel", required = false) RiskLevel riskLevel) {
        List<Refund> refunds = riskLevel == null
                ? refundService.listRefunds()
                : refundService.listRefundsByRisk(riskLevel);
        return refunds.stream().map(RefundResponse::from).toList();
    }

    @GetMapping("/refunds/{id}")
    public RefundResponse get(@PathVariable String id) {
        return RefundResponse.from(refundService.getRefund(id));
    }

    /** All refunds (full history) for a given payment. */
    @GetMapping("/payments/{paymentId}/refunds")
    public List<RefundResponse> listForPayment(@PathVariable String paymentId) {
        return refundService.listRefundsForPayment(paymentId).stream().map(RefundResponse::from).toList();
    }

    @PostMapping("/refunds/{id}/approve")
    public RefundResponse approve(@PathVariable String id) {
        return RefundResponse.from(approvalService.approve(id));
    }

    @PostMapping("/refunds/{id}/reject")
    public RefundResponse reject(@PathVariable String id) {
        return RefundResponse.from(approvalService.reject(id));
    }
}
