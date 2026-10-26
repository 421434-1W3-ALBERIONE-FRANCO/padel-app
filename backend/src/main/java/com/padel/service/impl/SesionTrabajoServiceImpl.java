package com.padel.service.impl;

import com.padel.domain.entity.BancoHoras;
import com.padel.domain.entity.MovimientoBancoHoras;
import com.padel.domain.entity.SesionTrabajo;
import com.padel.domain.entity.TurnoEmpleado;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoSesionTrabajo;
import com.padel.domain.enums.RolUsuario;
import com.padel.domain.enums.TipoMovimientoBancoHoras;
import com.padel.dto.response.BancoHorasResponse;
import com.padel.dto.response.FichajeResponse;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.OperacionNoPermitidaException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.repository.BancoHorasRepository;
import com.padel.repository.MovimientoBancoHorasRepository;
import com.padel.repository.SesionTrabajoRepository;
import com.padel.repository.TurnoEmpleadoRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.security.RedesPermitidas;
import com.padel.service.ConfiguracionSistemaService;
import com.padel.service.SesionTrabajoService;
import com.padel.util.EmailUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SesionTrabajoServiceImpl implements SesionTrabajoService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final SesionTrabajoRepository sesionTrabajoRepository;
    private final TurnoEmpleadoRepository turnoEmpleadoRepository;
    private final BancoHorasRepository bancoHorasRepository;
    private final MovimientoBancoHorasRepository movimientoBancoHorasRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionSistemaService configuracionSistemaService;
    private final RedesPermitidas redesPermitidas;

    @Override
    @Transactional
    public void cerrarSesionesHuerfanas() {
        List<SesionTrabajo> abiertas = sesionTrabajoRepository.findByEstado(EstadoSesionTrabajo.ABIERTA);
        log.info("Cerrando {} sesiones de trabajo huérfanas", abiertas.size());

        LocalTime topeConfig = configuracionSistemaService.obtenerEntidad().getHoraTopeCierreSesion();

        for (SesionTrabajo sesion : abiertas) {
            // Fin = hora fin del turno de ese día si existe, si no la hora tope configurada.
            LocalTime horaCierre = turnoEmpleadoRepository
                    .findByEmpleadoIdAndDiaSemana(sesion.getEmpleado().getId(), sesion.getInicio().getDayOfWeek())
                    .map(TurnoEmpleado::getHoraFin)
                    .orElse(topeConfig);

            LocalDateTime fin = sesion.getInicio().toLocalDate().atTime(horaCierre);
            // Nunca cerrar antes del inicio (si abrió después de la hora tope, se contabiliza 0 minutos).
            if (fin.isBefore(sesion.getInicio())) {
                fin = sesion.getInicio();
            }
            cerrarYAcreditar(sesion, fin, true);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BancoHorasResponse consultarBancoHoras(Long empleadoId) {
        Usuario empleado = usuarioRepository.findById(empleadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + empleadoId));
        int saldo = bancoHorasRepository.findByEmpleadoId(empleadoId)
                .map(BancoHoras::getSaldoMinutos)
                .orElse(0);
        return new BancoHorasResponse(empleadoId, empleado.getEmail(), saldo);
    }

    @Override
    @Transactional
    public FichajeResponse ficharEntrada(String email, String ip) {
        Usuario empleado = buscarStaff(email);
        validarRed(ip);
        sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(empleado.getId(), EstadoSesionTrabajo.ABIERTA)
                .ifPresent(abierta -> {
                    throw new EstadoInvalidoException("Ya fichaste la entrada a las " + HORA.format(abierta.getInicio()));
                });
        SesionTrabajo sesion = sesionTrabajoRepository.save(SesionTrabajo.builder()
                .empleado(empleado)
                .inicio(LocalDateTime.now())
                .estado(EstadoSesionTrabajo.ABIERTA)
                .cerradaAutomaticamente(false)
                .build());
        log.info("Entrada fichada por el empleado ID {}", empleado.getId());
        return aRespuesta(sesion);
    }

    @Override
    @Transactional
    public FichajeResponse ficharSalida(String email, String ip) {
        Usuario empleado = buscarStaff(email);
        validarRed(ip);
        SesionTrabajo abierta = sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(empleado.getId(), EstadoSesionTrabajo.ABIERTA)
                .orElseThrow(() -> new EstadoInvalidoException("No tenés una jornada abierta: fichá la entrada primero"));
        cerrarYAcreditar(abierta, LocalDateTime.now(), false);
        return aRespuesta(abierta);
    }

    @Override
    @Transactional(readOnly = true)
    public FichajeResponse fichajeActual(String email) {
        Usuario empleado = buscarStaff(email);
        return sesionTrabajoRepository.findFirstByEmpleadoIdAndEstado(empleado.getId(), EstadoSesionTrabajo.ABIERTA)
                .map(this::aRespuesta)
                .orElse(FichajeResponse.sinJornadaAbierta());
    }

    private Usuario buscarStaff(String email) {
        return usuarioRepository.findByEmail(EmailUtil.normalizar(email))
                .filter(u -> u.getRol() == RolUsuario.ADMIN || u.getRol() == RolUsuario.RECEPCIONISTA)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado no encontrado"));
    }

    private void validarRed(String ip) {
        if (!redesPermitidas.permite(ip)) {
            log.warn("Intento de fichaje desde una red no permitida");
            throw new OperacionNoPermitidaException("Solo se puede fichar desde la red del club");
        }
    }

    private FichajeResponse aRespuesta(SesionTrabajo sesion) {
        return new FichajeResponse(sesion.getId(), sesion.getEstado() == EstadoSesionTrabajo.ABIERTA,
                sesion.getInicio(), sesion.getFin(), sesion.getMinutosTrabajados());
    }

    private void cerrarYAcreditar(SesionTrabajo sesion, LocalDateTime fin, boolean automatica) {
        sesion.setFin(fin);
        sesion.setEstado(EstadoSesionTrabajo.CERRADA);
        sesion.setCerradaAutomaticamente(automatica);

        long minutosTrabajados = Duration.between(sesion.getInicio(), fin).toMinutes();
        sesion.setMinutosTrabajados((int) Math.max(0, minutosTrabajados));
        SesionTrabajo guardada = sesionTrabajoRepository.save(sesion);

        long extra = calcularMinutosExtra(guardada, fin);
        if (extra > 0) {
            acreditarBancoHoras(guardada, (int) extra);
        }

        log.info("Sesión de trabajo ID {} cerrada. Minutos trabajados: {}, minutos extra acreditados: {}",
                guardada.getId(), guardada.getMinutosTrabajados(), extra);
    }

    // Minutos extra = apertura antes del inicio de turno + cierre después del fin de turno.
    // Sin turno asignado ese día no hay baseline, por lo tanto no se acredita nada (decisión de diseño).
    private long calcularMinutosExtra(SesionTrabajo sesion, LocalDateTime fin) {
        Optional<TurnoEmpleado> turnoOpt = turnoEmpleadoRepository
                .findByEmpleadoIdAndDiaSemana(sesion.getEmpleado().getId(), sesion.getInicio().getDayOfWeek());
        if (turnoOpt.isEmpty()) {
            return 0;
        }
        TurnoEmpleado turno = turnoOpt.get();
        LocalTime inicioTime = sesion.getInicio().toLocalTime();
        LocalTime finTime = fin.toLocalTime();

        long extra = 0;
        if (inicioTime.isBefore(turno.getHoraInicio())) {
            extra += Duration.between(inicioTime, turno.getHoraInicio()).toMinutes();
        }
        if (finTime.isAfter(turno.getHoraFin())) {
            extra += Duration.between(turno.getHoraFin(), finTime).toMinutes();
        }
        return extra;
    }

    private void acreditarBancoHoras(SesionTrabajo sesion, int minutos) {
        BancoHoras banco = bancoHorasRepository.findByEmpleadoId(sesion.getEmpleado().getId())
                .orElseGet(() -> BancoHoras.builder()
                        .empleado(sesion.getEmpleado())
                        .saldoMinutos(0)
                        .build());
        banco.setSaldoMinutos(banco.getSaldoMinutos() + minutos);
        bancoHorasRepository.save(banco);

        MovimientoBancoHoras movimiento = MovimientoBancoHoras.builder()
                .empleado(sesion.getEmpleado())
                .sesionTrabajo(sesion)
                .minutos(minutos)
                .tipo(TipoMovimientoBancoHoras.ACREDITACION)
                .liquidado(false)
                .build();
        movimientoBancoHorasRepository.save(movimiento);
    }
}
