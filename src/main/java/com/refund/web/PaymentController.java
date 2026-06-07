package com.refund.web;

import com.refund.domain.Payment;
import com.refund.exception.ApiException;
import com.refund.repository.PaymentRepository;
import com.refund.web.dto.PaymentResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping
    public List<PaymentResponse> list() {
        return paymentRepository.findAll().stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable String id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("PAYMENT_NOT_FOUND", "No payment with id " + id));
        return PaymentResponse.from(payment);
    }
}
