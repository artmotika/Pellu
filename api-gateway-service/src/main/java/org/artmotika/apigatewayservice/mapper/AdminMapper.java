package org.artmotika.apigatewayservice.mapper;

import org.artmotika.common.dto.AssetCreateRequestDto;
import org.artmotika.common.dto.AssetDto;
import org.artmotika.common.dto.AssetStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

@Mapper(componentModel = "spring", imports = {UUID.class, AssetStatus.class})
public interface AdminMapper {
    AdminMapper INSTANCE = Mappers.getMapper(AdminMapper.class);

    @Mapping(target = "id", expression = "java(UUID.randomUUID().toString())")
    @Mapping(target = "status", expression = "java(AssetStatus.IPO_PLANNED)")
    @Mapping(target = "solanaMintAddress", expression = "java(\"MOCK_MINT_\" + UUID.randomUUID().toString().substring(0, 8))")
    @Mapping(target = "legalDocHash", source = "legalDocHash", defaultValue = "MOCK_HASH")
    @Mapping(target = "tradeUnlockTimestamp", expression = "java(req.getTradeUnlockTimestamp() != null ? req.getTradeUnlockTimestamp() : System.currentTimeMillis() / 1000 + 3600)")
    AssetDto toAssetDto(AssetCreateRequestDto req);
}
