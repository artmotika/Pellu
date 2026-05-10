package org.artmotika.apigatewayservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.artmotika.common.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false) // Disable security for IT
public class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private KafkaTemplate<String, OrderRequestDto> kafkaTemplate;

    @MockBean
    private java.util.List<org.artmotika.apigatewayservice.service.validator.OrderValidator> validators;

    private MockRestServiceServer mockServer;

    @BeforeEach
    void init() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void testFullOrderSubmissionFlow() throws Exception {
        // 1. Mock Auth Service Response
        UserDto user = UserDto.builder()
                .id("u1")
                .walletAddress("wallet123")
                .kycStatus(KycStatus.APPROVED)
                .amlRiskScore(0)
                .qualified(true) // Bypass investor limit validator for simplicity
                .build();

        mockServer.expect(requestTo("http://auth-service:8083/api/v1/auth/users/u1"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(user), MediaType.APPLICATION_JSON));

        // Mock Trading Service Response (for AssetValidator)
        AssetDto asset = new AssetDto();
        asset.setId("a1");
        asset.setStatus(AssetStatus.TRADING);
        mockServer.expect(requestTo("http://trading-service:8081/api/assets/a1"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(asset), MediaType.APPLICATION_JSON));

        // 2. Prepare Order Request
        OrderRequestDto orderRequest = new OrderRequestDto();
        orderRequest.setUserId("u1");
        orderRequest.setAssetId("a1");
        orderRequest.setType(OrderType.BUY);
        orderRequest.setAmount(new BigDecimal("10"));
        orderRequest.setPrice(new BigDecimal("100"));

        // 3. Execute POST /api/v1/orders
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orderRequest)))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isAccepted());

        // 4. Verify Kafka Event
        verify(kafkaTemplate).send(eq("orders.created"), argThat(o -> 
            o.getUserId().equals("u1") && 
            o.getWalletAddress().equals("wallet123")
        ));
    }
}
