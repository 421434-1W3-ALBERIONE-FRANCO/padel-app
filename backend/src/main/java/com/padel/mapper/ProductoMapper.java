package com.padel.mapper;

import com.padel.domain.entity.Producto;
import com.padel.dto.request.ProductoRequest;
import com.padel.dto.response.ProductoResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ProductoMapper {
    Producto toEntity(ProductoRequest request);
    ProductoResponse toResponse(Producto entity);
    void updateFromRequest(ProductoRequest request, @MappingTarget Producto entity);
}
