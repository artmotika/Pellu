package org.artmotika.tradingengineservice.mapper;

import org.artmotika.common.dto.AssetDto;
import org.artmotika.tradingengineservice.model.Asset;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface AssetMapper {
    AssetMapper INSTANCE = Mappers.getMapper(AssetMapper.class);

    AssetDto toDto(Asset asset);
    Asset toEntity(AssetDto dto);
}
