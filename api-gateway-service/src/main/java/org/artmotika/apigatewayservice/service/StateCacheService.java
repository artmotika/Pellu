package org.artmotika.apigatewayservice.service;

import lombok.RequiredArgsConstructor;
import org.artmotika.common.dto.AssetDto;
import org.artmotika.common.dto.InvestorLimitDto;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class StateCacheService {
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LIMITS_KEY = "investor:limits";
    private static final String ASSETS_KEY = "assets:state";

    public InvestorLimitDto getUserLimit(String userId) {
        return (InvestorLimitDto) redisTemplate.opsForHash().get(LIMITS_KEY, userId);
    }

    public AssetDto getAsset(String assetId) {
        return (AssetDto) redisTemplate.opsForHash().get(ASSETS_KEY, assetId);
    }
}
