package com.padel.service.impl;

import com.mercadopago.client.point.PointClient;
import com.mercadopago.client.point.PointPaymentIntentRequest;
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
import com.padel.service.ConsumoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ConsumoServiceImpl implements ConsumoService {

    private final ConsumoRepository consumoRepository;
    private final ReservaRepository reservaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoRepository pagoRepository;
    private final ConsumoMapper consumoMapper;

    @Override
    public ConsumoResponse cargarConsumo(Long reservaId, ConsumoRequest request) {
        log.info("Cargando consumo para reserva ID: {}, producto ID: {}", reservaId, request.productoId());

        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));

        Producto producto = productoRepository.findById(request.productoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + request.productoId()));

        if (!producto.isActivo()) {
            throw new ResourceNotFoundException("El producto seleccionado no está activo");
        }

        if (producto.getStock() < request.cantidad()) {
            throw new StockInsuficienteException("Stock insuficiente para el producto: " + producto.getNombre() + ". Stock disponible: " + producto.getStock());
        }

        // Descontar stock de inmediato
        producto.setStock(producto.getStock() - request.cantidad());
        productoRepository.save(producto);

        BigDecimal subtotal = producto.getPrecio().multiply(BigDecimal.valueOf(request.cantidad()));

        Consumo consumo = Consumo.builder()
                .reserva(reserva)
                .usuario(reserva.getUsuario())
                .producto(producto)
                .cantidad(request.cantidad())
                .precioUnitario(producto.getPrecio())
                .subtotal(subtotal)
                .estadoPago(EstadoConsumoPago.PENDIENTE)
                .build();

        Consumo saved = consumoRepository.save(consumo);
        log.info("Consumo ID {} cargado exitosamente", saved.getId());

        return consumoMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsumoResponse> obtenerConsumosPorReserva(Long reservaId, String usuarioEmail) {
        log.info("Obteniendo consumos para la reserva ID: {} por usuario {}", reservaId, usuarioEmail);

        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));

        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        boolean isAdminOrRecepcion = usuario.getRol() == RolUsuario.ADMIN || usuario.getRol() == RolUsuario.RECEPCIONISTA;
        if (!isAdminOrRecepcion && !reserva.getUsuario().getId().equals(usuario.getId())) {
            throw new ResourceNotFoundException("Reserva no encontrada para el usuario especificado");
        }

        return consumoRepository.findByReservaId(reservaId).stream()
                .map(consumoMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public void cerrarCuenta(Long reservaId, CerrarCuentaRequest request) {
        log.info("Cerrando cuenta para reserva ID: {} con método {}", reservaId, request.metodo());

        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));

        List<Consumo> pendientes = consumoRepository.findByReservaId(reservaId).stream()
                .filter(c -> c.getEstadoPago() == EstadoConsumoPago.PENDIENTE)
                .toList();

        if (pendientes.isEmpty()) {
            log.info("No hay consumos pendientes para la reserva ID: {}", reservaId);
            return;
        }

        BigDecimal total = pendientes.stream()
                .map(Consumo::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        EstadoPago estadoPago = request.metodo() == MetodoPago.EFECTIVO ? EstadoPago.APROBADO : EstadoPago.PENDIENTE;

        Pago pago = Pago.builder()
                .reserva(reserva)
                .usuario(reserva.getUsuario())
                .monto(total)
                .metodo(request.metodo())
                .estado(estadoPago)
                .build();

        // Si es MercadoPago Point, intentar la llamada presencial
        if (request.metodo() == MetodoPago.MERCADOPAGO_POINT) {
            try {
                log.info("Iniciando cobro presencial vía MercadoPago Point por monto {}", total);
                PointClient pointClient = new PointClient();
                PointPaymentIntentRequest mpRequest = PointPaymentIntentRequest.builder()
                        .amount(total)
                        .description("Consumos Reserva #" + reservaId)
                        .build();

                // Intentamos crear la intención de pago presencial con un ID de dispositivo por defecto
                // Capturamos el error si no hay un terminal real configurado, lo cual es normal en sandbox local
                pointClient.createPaymentIntent("SANDBOX-DEVICE-01", mpRequest);
            } catch (Exception e) {
                log.warn("MercadoPago Point API integration simulation (sandbox connection fail): {}", e.getMessage());
            }
        }

        Pago savedPago = pagoRepository.save(pago);

        for (Consumo c : pendientes) {
            c.setPago(savedPago);
            if (request.metodo() == MetodoPago.EFECTIVO) {
                c.setEstadoPago(EstadoConsumoPago.PAGADO);
            }
        }

        consumoRepository.saveAll(pendientes);
        log.info("Cuenta cerrada para reserva ID {}. Pago ID {} en estado {}", reservaId, savedPago.getId(), estadoPago);
    }

    @Override
    public void marcarConsumosComoPagados(Long pagoId) {
        log.info("Actualizando consumos vinculados al Pago ID {} a estado PAGADO", pagoId);
        List<Consumo> consumos = consumoRepository.findByPagoId(pagoId);
        for (Consumo c : consumos) {
            c.setEstadoPago(EstadoConsumoPago.PAGADO);
        }
        consumoRepository.saveAll(consumos);
        log.info("Total de {} consumos actualizados a PAGADO", consumos.size());
    }
}
