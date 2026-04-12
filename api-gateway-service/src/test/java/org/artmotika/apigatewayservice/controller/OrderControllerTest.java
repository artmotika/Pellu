package org.artmotika.apigatewayservice.controller;

import org.artmotika.apigatewayservice.service.AmlKycService;
import org.artmotika.common.dto.OrderRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private AmlKycService amlKycService;

    @InjectMocks
    private OrderController orderController;

    @Test
    void submitOrder_ShouldCallService() {
        OrderRequestDto order = new OrderRequestDto();
        String result = orderController.submitOrder(order);
        
        assertEquals("Order Accepted", result);
        verify(amlKycService, times(1)).processOrder(order);
    }
}
