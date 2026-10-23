package com.padel.service.impl;

import com.padel.domain.entity.TipoBono;
import com.padel.dto.request.TipoBonoRequest;
import com.padel.exception.NombreDuplicadoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.TipoBonoMapper;
import com.padel.repository.TipoBonoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TipoBonoServiceImplTest {

    @Mock
    private TipoBonoRepository repository;
    @Mock
    private TipoBonoMapper mapper;

    @InjectMocks
    private TipoBonoServiceImpl service;

    private TipoBonoRequest request(String nombre, Boolean activo) {
        return new TipoBonoRequest(nombre, "Pack de prueba", 10, new BigDecimal("8000.00"), 30, activo);
    }

    @Test
    void listar_SoloActivosSalvoQueSePidanTodos() {
        when(repository.findByActivoTrueOrderByHorasAsc()).thenReturn(List.of());
        when(repository.findAllByOrderByHorasAsc()).thenReturn(List.of());

        service.listar(false);
        service.listar(true);

        verify(repository).findByActivoTrueOrderByHorasAsc();
        verify(repository).findAllByOrderByHorasAsc();
    }

    @Test
    void crear_ActivoPorDefectoYConElNombreSinEspacios() {
        when(repository.existsByNombreIgnoreCase("Pack Verano")).thenReturn(false);
        when(repository.save(any(TipoBono.class))).thenAnswer(inv -> inv.getArgument(0));

        service.crear(request("  Pack Verano ", null));

        ArgumentCaptor<TipoBono> captor = ArgumentCaptor.forClass(TipoBono.class);
        verify(repository).save(captor.capture());
        assertEquals("Pack Verano", captor.getValue().getNombre());
        assertTrue(captor.getValue().isActivo());
        assertEquals(30, captor.getValue().getDiasVigencia());
    }

    @Test
    void crear_ConNombreRepetidoSeRechaza() {
        when(repository.existsByNombreIgnoreCase("10HS")).thenReturn(true);

        assertThrows(NombreDuplicadoException.class, () -> service.crear(request("10HS", true)));
        verify(repository, never()).save(any());
    }

    @Test
    void actualizar_NoPermiteRobarElNombreDeOtroTipo() {
        TipoBono existente = TipoBono.builder().id(2L).nombre("20HS").horas(20)
                .precio(new BigDecimal("15000.00")).diasVigencia(30).activo(true).build();
        when(repository.findById(2L)).thenReturn(Optional.of(existente));
        when(repository.existsByNombreIgnoreCaseAndIdNot("10HS", 2L)).thenReturn(true);

        assertThrows(NombreDuplicadoException.class, () -> service.actualizar(2L, request("10HS", true)));
        verify(repository, never()).save(any());
    }

    @Test
    void desactivar_DejaElTipoFueraDeLaVentaSinBorrarlo() {
        TipoBono existente = TipoBono.builder().id(2L).nombre("20HS").horas(20)
                .precio(new BigDecimal("15000.00")).diasVigencia(30).activo(true).build();
        when(repository.findById(2L)).thenReturn(Optional.of(existente));

        service.desactivar(2L);

        assertFalse(existente.isActivo());
        verify(repository).save(existente);
        verify(repository, never()).delete(any());
    }

    @Test
    void desactivar_UnTipoInexistenteDa404() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.desactivar(99L));
    }
}
