package org.artmotika.authservice.mapper;

import org.artmotika.authservice.model.User;
import org.artmotika.common.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserMapper INSTANCE = Mappers.getMapper(UserMapper.class);

    UserDto toDto(User user);
    
    @Mapping(target = "password", ignore = true)
    User toEntity(UserDto dto);
}
