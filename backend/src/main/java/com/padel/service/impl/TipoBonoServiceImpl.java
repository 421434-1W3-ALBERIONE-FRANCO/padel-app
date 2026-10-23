package com.padel.service.impl;

import com.padel.domain.entity.TipoBono;
import com.padel.dto.request.TipoBonoRequest;
import com.padel.dto.response.TipoBonoResponse;
import com.padel.exception.NombreDuplicadoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.TipoBonoMapper;
import com.padel.repository.TipoBonoRepository;
import com.padel.service.TipoBonoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class TipoBonoServiceImpl implements TipoBonoService {

    private final TipoBonoRepository repository;
    private final TipoBonoMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<TipoBonoResponse> listar(boolean incluirInactivos) {
        List<TipoBono> tipos = incluirInactivos ? repository.findAllByOrderByHorasAsc() : repository.findByActivoTrueOrderByHorasAsc();
        return tipos.stream().map(mapper::toResponse).toList();
    }

    @Override
    public TipoBonoResponse crear(TipoBonoRequest request) {
        String nombre = request.nombre().trim();
        if (repository.existsByNombreIgnoreCase(nombre)) {
            throw new NombreDuplicadoException("Ya existe un tipo de bono llamado " + nombre);
        }
        TipoBono tipo = TipoBono.builder()
                .nombre(nombre)
                .descripcion(request.descripcion())
                .horas(request.horas())
                .precio(request.precio())
                .diasVigencia(request.diasVigencia())
                .activo(request.activo() == null || request.activo())
                .build();
        return mapper.toResponse(repository.save(tipo));
    }

    @Override
    public TipoBonoResponse actualizar(Long id, TipoBonoRequest request) {
        TipoBono tipo = buscar(id);
        String nombre = request.nombre().trim();
        if (repository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new NombreDuplicadoException("Ya existe otro tipo de bono llamado " + nombre);
        }
        tipo.setNombre(nombre);
        tipo.setDescripcion(request.descripcion());
        tipo.setHoras(request.horas());
        tipo.setPrecio(request.precio());
        tipo.setDiasVigencia(request.diasVigencia());
        if (request.activo() != null) {
            tipo.setActivo(request.activo());
        }
        return mapper.toResponse(repository.save(tipo));
    }

    @Override
    public void desactivar(Long id) {
        TipoBono tipo = buscar(id);
        tipo.setActivo(false);
        repository.save(tipo);
    }

    private TipoBono buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de bono no encontrado con ID: " + id));
    }
}
