package com.refund;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Thin HTTP-layer checks: status codes and the error envelope. */
@SpringBootTest
@AutoConfigureMockMvc
class RefundApiTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createRefund_returns201() throws Exception {
        mockMvc.perform(post("/payments/pay_adyen_captured_750/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00, \"reason\": \"customer request\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amount").value(100.00));
    }

    @Test
    void refundOnAuthorizedPayment_returns422WithCode() throws Exception {
        mockMvc.perform(post("/payments/pay_adyen_authorized_300/refunds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 50.00}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_REFUNDABLE"));
    }

    @Test
    void unknownPayment_returns404() throws Exception {
        mockMvc.perform(get("/payments/does_not_exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void root_redirectsToSwaggerUi() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
    }

    @Test
    void health_returnsUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
