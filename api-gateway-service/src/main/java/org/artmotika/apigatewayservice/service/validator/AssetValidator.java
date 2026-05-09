package org.artmotika.apigatewayservice.service.validator;

import lombok.RequiredArgsConstructor;
import org.artmotika.apigatewayservice.exception.AmlViolationException;
import org.artmotika.common.dto.AssetDto;
import org.artmotika.common.dto.AssetStatus;
import org.artmotika.common.dto.OrderRequestDto;
import org.artmotika.common.dto.UserDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class AssetValidator implements OrderValidator {
    private final RestTemplate restTemplate;

    @Value("${app.services.trading}")
    private String tradingServiceUrl;

    @Override
    public void validate(OrderRequestDto order, UserDto user) {
        AssetDto asset = restTemplate.getForObject(tradingServiceUrl + "/api/assets/" + order.getAssetId(), AssetDto.class);

        if (asset == null) {
            throw new AmlViolationException("Asset not found via Trading Service");
        }

        if (asset.getStatus() == AssetStatus.IPO_PLANNED) {
            throw new AmlViolationException("Asset is not yet available for purchase (IPO Planned)");
        }

        if (asset.getStatus() == AssetStatus.SUSPENDED) {
            throw new AmlViolationException("Trading is suspended for this asset");
        }

        if (asset.getTradeUnlockTimestamp() != null && asset.getTradeUnlockTimestamp() > System.currentTimeMillis() / 1000) {
            throw new AmlViolationException("Trading is locked until " + asset.getTradeUnlockTimestamp());
        }

        if (asset.getStatus() == AssetStatus.IPO_ACTIVE) {
            if (order.getType().name().equals("SELL")) {
                throw new AmlViolationException("Selling is not allowed during IPO phase");
            }
            if (asset.getIpoPrice() != null && order.getPrice().compareTo(asset.getIpoPrice()) != 0) {
                throw new AmlViolationException("Orders during IPO must be at the fixed IPO price: " + asset.getIpoPrice());
            }
        }
    }
}
