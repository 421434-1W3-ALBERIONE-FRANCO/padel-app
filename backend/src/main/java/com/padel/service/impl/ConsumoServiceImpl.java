package com.padel.service.impl;

import com.padel.domain.entity.Consumo;
import com.padel.domain.entity.Pago;
import com.padel.domain.entity.Producto;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.ConceptoPago;
import com.padel.domain.enums.EstadoConsumoPago;
import com.padel.domain.enums.EstadoPago;
import com.padel.domain.enums.EstadoReserva;
import com.padel.domain.enums.MetodoPago;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.CerrarCuentaRequest;
import com.padel.dto.request.ConsumoRequest;
import com.padel.dto.response.ConsumoResponse;
import com.padel.exception.DatosInvalidosException;
import com.padel.exception.ReservaNoModificableException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.integracion.pagos.PasarelaPagos;
import com.padel.mapper.ConsumoMapper;
import com.padel.repository.ConsumoRepository;
import com.padel.repository.PagoRepository;
import com.padel.repository.ProductoRepository;
import com.padel.repository.ReservaRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.security.ControlAcceso;
import com.padel.service.ComisionService;
import com.padel.service.ConfiguracionSistemaService;
import com.padel.service.ConsumoService;
import com.padel.util.EmailUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

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
    private final ConfiguracionSistemaService configuracionSistemaService;
    private final ComisionService comisionService;
    private final ControlAcceso controlAcceso;
    private final PasarelaPagos pasarelaPagos;

    // Terminal MercadoPago Point del club. Vacío = el cobro con Point no está habilitado (antes se usaba un id de
    // dispositivo inventado y el error se ignoraba: el cobro quedaba pendiente para siempre).
    @Value("${mercadopago.point.device-id:}")
    private String dispositivoPoint;

    @Override
    public ConsumoResponse cargarConsumo(Long reservaId, ConsumoRequest request) {
        log.info("Cargando consumo para reserva ID: {}, producto ID: {}", reservaId, request.productoId());

        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));
        if (reserva.getEstadoReserva() == EstadoReserva.CANCELADA) {
            throw new ReservaNoModificableException("No se pueden cargar consumos a una reserva cancelada");
        }

        // Bloqueo del producto mientras se descuenta stock: dos cargas simultáneas podían vender de más.
        Producto producto = productoRepository.findByIdForUpdate(request.productoId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado con ID: " + request.productoId()));
        if (!producto.isActivo()) {
            throw new ResourceNotFoundException("El producto seleccionado no está activo");
        }
        producto.descontarStock(request.cantidad());
        productoRepository.save(producto);

        // Si el producto ya tiene una fila pendiente en este tab, acumular cantidad en vez de duplicar fila
        Consumo consumo = consumoRepository
                .findFirstByReserva_IdAndProducto_IdAndEstadoPagoAndPagoIsNull(reservaId, request.productoId(), EstadoConsumoPago.PENDIENTE)
                .map(existente -> {
                    existente.setCantidad(existente.getCantidad() + request.cantidad());
                    existente.setSubtotal(existente.getPrecioUnitario().multiply(BigDecimal.valueOf(existente.getCantidad())));
                    return existente;
                })
                .orElseGet(() -> Consumo.builder()
                        .reserva(reserva)
                        .usuario(reserva.getUsuario())
                        .producto(producto)
                        .cantidad(request.cantidad())
                        .precioUnitario(producto.getPrecio())
                        .subtotal(producto.getPrecio().multiply(BigDecimal.valueOf(request.cantidad())))
                        .estadoPago(EstadoConsumoPago.PENDIENTE)
                        .build());

        Consumo saved = consumoRepository.save(consumo);
        log.info("Consumo ID {} cargado", saved.getId());
        return consumoMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsumoResponse> obtenerConsumosPorReserva(Long reservaId, String usuarioEmail) {
        Reserva reserva = reservaRepository.findById(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));
        Usuario usuario = usuarioRepository.findByEmail(EmailUtil.normalizar(usuarioEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        controlAcceso.exigirDuenoOStaff(usuario, reserva.getUsuario(), "Reserva");

        return consumoRepository.findByReservaId(reservaId).stream()
                .map(consumoMapper::toResponse)
                .toList();
    }

    @Override
    public void cerrarCuenta(Long reservaId, CerrarCuentaRequest request, String staffEmail) {
        MetodoPago metodo = request.metodo();
        if (!metodo.esCobroEnMostrador() && metodo != MetodoPago.MERCADOPAGO_POINT) {
            throw new DatosInvalidosException("La cuenta se cobra en efectivo, con tarjeta o con la terminal Point");
        }
        if (metodo == MetodoPago.MERCADOPAGO_POINT && (dispositivoPoint == null || dispositivoPoint.isBlank())) {
            throw new DatosInvalidosException("El cobro con MercadoPago Point no está configurado en este club");
        }

        // Bloqueo de la reserva: dos cierres simultáneos (doble click) generaban dos pagos por los mismos consumos.
        Reserva reserva = reservaRepository.findByIdForUpdate(reservaId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada con ID: " + reservaId));
        Usuario staff = usuarioRepository.findByEmail(EmailUtil.normalizar(staffEmail))
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Solo los consumos sin pago asociado: los que ya se mandaron a cobrar al Point siguen PENDIENTE pero
        // tienen su pago en curso y no deben volver a sumarse.
        List<Consumo> pendientes = consumoRepository.findByReservaId(reservaId).stream()
                .filter(c -> c.getEstadoPago() == EstadoConsumoPago.PENDIENTE && c.getPago() == null)
                .toList();
        if (pendientes.isEmpty()) {
            log.info("No hay consumos pendientes para la reserva ID: {}", reservaId);
            return;
        }

        BigDecimal total = pendientes.stream()
                .map(Consumo::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean cobroInmediato = metodo.esCobroEnMostrador();

        Pago pago = pagoRepository.saveAndFlush(Pago.builder()
                .reserva(reserva)
                .usuario(reserva.getUsuario())
                .monto(total)
                .metodo(metodo)
                .estado(cobroInmediato ? EstadoPago.APROBADO : EstadoPago.PENDIENTE)
                .concepto(ConceptoPago.CONSUMOS)
                .atendidoPor(staff)
                .build());

        if (metodo == MetodoPago.MERCADOPAGO_POINT) {
            // La referencia permite que el webhook de MercadoPago impute el cobro a este pago.
            pasarelaPagos.enviarCobroAlPoint(dispositivoPoint, total, "Consumos reserva #" + reservaId,
                    PagoServiceImpl.referenciaConsumos(pago.getId()));
        }

        if (staff.getRol() == RolUsuario.RECEPCIONISTA && configuracionSistemaService.obtenerEntidad().isComisionesHabilitadas()) {
            comisionService.registrarComisionConsumo(staff, pago, total);
        }

        for (Consumo c : pendientes) {
            c.setPago(pago);
            if (cobroInmediato) {
                c.setEstadoPago(EstadoConsumoPago.PAGADO);
            }
        }
        consumoRepository.saveAll(pendientes);
        log.info("Cuenta cerrada para la reserva ID {}: pago ID {} en estado {}", reservaId, pago.getId(), pago.getEstado());
    }

    @Override
    public void marcarConsumosComoPagados(Long pagoId) {
        List<Consumo> consumos = consumoRepository.findByPagoId(pagoId);
        consumos.forEach(c -> c.setEstadoPago(EstadoConsumoPago.PAGADO));
        consumoRepository.saveAll(consumos);
        log.info("{} consumos del pago ID {} marcados como PAGADO", consumos.size(), pagoId);
    }

    @Override
    public void liberarConsumosDelPago(Long pagoId) {
        List<Consumo> consumos = consumoRepository.findByPagoId(pagoId).stream()
                .filter(c -> c.getEstadoPago() == EstadoConsumoPago.PENDIENTE)
                .toList();
        consumos.forEach(c -> c.setPago(null));
        consumoRepository.saveAll(consumos);
    }
}
