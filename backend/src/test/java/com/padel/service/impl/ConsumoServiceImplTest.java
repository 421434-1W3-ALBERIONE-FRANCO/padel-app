package com.padel.service.impl;

import com.padel.domain.entity.*;
import com.padel.domain.enums.EstadoConsumoPago;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CerrarCuentaRequest;
import com.padel.dto.request.ConsumoRequest;
import com.padel.dto.response.ConsumoResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.exception.StockInsuficienteException;
import com.padel.mapper.ConsumoMapper;
import com.padel.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsumoServiceImplTest {

    @Mock
    private ConsumoRepository consumoRepository;
    @Mock
    private ReservaRepository reservaRepository;
    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private ConsumoMapper consumoMapper;

    @InjectMocks
    private ConsumoServiceImpl consumoService;

    private Reserva reservaMock;
    private Usuario usuarioMock;
    private Producto productoMock;
    private Consumo consumoMock;
    private ConsumoRequest consumoRequest;
    private ConsumoResponse consumoResponse;

    @BeforeEach
    void setUp() {
        usuarioMock = Usuario.builder()
                .id(1L)
                .email("juan@example.com")
                .rol(RolUsuario.JUGADOR)
                .build();

        reservaMock = Reserva.builder()
                .id(1L)
                .usuario(usuarioMock)
                .precioTotal(new BigDecimal("1200.00"))
                .build();

        productoMock = Producto.builder()
                .id(1L)
                .nombre("Agua Mineral")
                .categoria("BEBIDA")
                .precio(new BigDecimal("100.00"))
                .stock(5)
                .activo(true)
                .build();

        consumoMock = Consumo.builder()
                .id(1L)
                .reserva(reservaMock)
                .usuario(usuarioMock)
                .producto(productoMock)
                .cantidad(2)
                .precioUnitario(new BigDecimal("100.00"))
                .subtotal(new BigDecimal("200.00"))
                .estadoPago(EstadoConsumoPago.PENDIENTE)
                .build();

        consumoRequest = new ConsumoRequest(1L, 2);

        consumoResponse = new ConsumoResponse(
                1L, 1L, 1L, "juan@example.com", 1L, "Agua Mineral", null,
                2, new BigDecimal("100.00"), new BigDecimal("200.00"), EstadoConsumoPago.PENDIENTE, null
        );
    }

    @Test
    void cargarConsumo_DeberiaDescontarStockYGuardarConsumo() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(productoRepository.findById(1L)).thenReturn(Optional.of(productoMock));
        when(productoRepository.save(any(Producto.class))).thenReturn(productoMock);
        when(consumoRepository.save(any(Consumo.class))).thenReturn(consumoMock);
        when(consumoMapper.toResponse(any(Consumo.class))).thenReturn(consumoResponse);

        ConsumoResponse result = consumoService.cargarConsumo(1L, consumoRequest);

        assertNotNull(result);
        assertEquals(3, productoMock.getStock()); // 5 - 2 = 3
        verify(productoRepository, times(1)).save(productoMock);
        verify(consumoRepository, times(1)).save(any(Consumo.class));
    }

    @Test
    void cargarConsumo_DeberiaLanzarStockInsuficienteException() {
        productoMock.setStock(1);
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(productoRepository.findById(1L)).thenReturn(Optional.of(productoMock));

        assertThrows(StockInsuficienteException.class, () -> consumoService.cargarConsumo(1L, consumoRequest));
        verify(productoRepository, never()).save(any(Producto.class));
        verify(consumoRepository, never()).save(any(Consumo.class));
    }

    @Test
    void obtenerConsumosPorReserva_DeberiaRetornarConsumosSiEsElDueno() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(usuarioMock));
        when(consumoRepository.findByReservaId(1L)).thenReturn(Collections.singletonList(consumoMock));
        when(consumoMapper.toResponse(any(Consumo.class))).thenReturn(consumoResponse);

        List<ConsumoResponse> result = consumoService.obtenerConsumosPorReserva(1L, "juan@example.com");

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void obtenerConsumosPorReserva_DeberiaLanzarExcepcionSiEsOtroUsuario() {
        Usuario otroUsuario = Usuario.builder().id(2L).email("other@example.com").rol(RolUsuario.JUGADOR).build();
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        when(usuarioRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otroUsuario));

        assertThrows(ResourceNotFoundException.class, () -> consumoService.obtenerConsumosPorReserva(1L, "other@example.com"));
    }

    @Test
    void cerrarCuenta_Efectivo_DeberiaCrearPagoAprobadoYMarcarConsumosPagados() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        List<Consumo> pendientes = new ArrayList<>();
        pendientes.add(consumoMock);
        when(consumoRepository.findByReservaId(1L)).thenReturn(pendientes);

        Pago pagoMock = Pago.builder().id(10L).reserva(reservaMock).usuario(usuarioMock).monto(new BigDecimal("200.00")).metodo(MetodoPago.EFECTIVO).estado(EstadoPago.APROBADO).build();
        when(pagoRepository.save(any(Pago.class))).thenReturn(pagoMock);

        consumoService.cerrarCuenta(1L, new CerrarCuentaRequest(MetodoPago.EFECTIVO));

        assertEquals(EstadoConsumoPago.PAGADO, consumoMock.getEstadoPago());
        assertNotNull(consumoMock.getPago());
        verify(pagoRepository, times(1)).save(any(Pago.class));
        verify(consumoRepository, times(1)).saveAll(anyList());
    }

    @Test
    void cerrarCuenta_MercadoPagoPoint_DeberiaCrearPagoPendienteYDejarConsumosPendiente() {
        when(reservaRepository.findById(1L)).thenReturn(Optional.of(reservaMock));
        List<Consumo> pendientes = new ArrayList<>();
        pendientes.add(consumoMock);
        when(consumoRepository.findByReservaId(1L)).thenReturn(pendientes);

        Pago pagoMock = Pago.builder().id(10L).reserva(reservaMock).usuario(usuarioMock).monto(new BigDecimal("200.00")).metodo(MetodoPago.MERCADOPAGO_POINT).estado(EstadoPago.PENDIENTE).build();
        when(pagoRepository.save(any(Pago.class))).thenReturn(pagoMock);

        consumoService.cerrarCuenta(1L, new CerrarCuentaRequest(MetodoPago.MERCADOPAGO_POINT));

        assertEquals(EstadoConsumoPago.PENDIENTE, consumoMock.getEstadoPago()); // Continúa pendiente hasta webhook
        assertNotNull(consumoMock.getPago());
        verify(pagoRepository, times(1)).save(any(Pago.class));
    }

    @Test
    void marcarConsumosComoPagados_DeberiaPasarAPagado() {
        List<Consumo> consumos = Collections.singletonList(consumoMock);
        when(consumoRepository.findByPagoId(10L)).thenReturn(consumos);

        consumoService.marcarConsumosComoPagados(10L);

        assertEquals(EstadoConsumoPago.PAGADO, consumoMock.getEstadoPago());
        verify(consumoRepository, times(1)).saveAll(consumos);
    }
}
