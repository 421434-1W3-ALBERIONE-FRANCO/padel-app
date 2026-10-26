package com.padel.repository;

import com.padel.domain.entity.Cancha;
import com.padel.domain.entity.Reserva;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.EstadoReserva;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filtros del historial de reservas del staff, combinables y opcionales. */
public final class ReservaSpecs {

    private ReservaSpecs() {
    }

    public static Specification<Reserva> filtrar(LocalDate desde, LocalDate hasta, EstadoReserva estado, String texto) {
        return (root, query, cb) -> {
            // En la consulta de datos se traen usuario y cancha en el mismo SELECT (el mapper los lee);
            // en la de conteo de la paginación alcanza con un join simple.
            Join<Reserva, Usuario> usuario;
            Join<Reserva, Cancha> cancha;
            boolean esConteo = Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType());
            if (esConteo) {
                usuario = root.join("usuario", JoinType.INNER);
                cancha = root.join("cancha", JoinType.INNER);
            } else {
                // En Hibernate el fetch también es un join: se reutiliza para filtrar sin un segundo JOIN.
                Fetch<Reserva, Usuario> fetchUsuario = root.fetch("usuario", JoinType.INNER);
                Fetch<Reserva, Cancha> fetchCancha = root.fetch("cancha", JoinType.INNER);
                usuario = (Join<Reserva, Usuario>) fetchUsuario;
                cancha = (Join<Reserva, Cancha>) fetchCancha;
            }

            List<Predicate> predicados = new ArrayList<>();
            if (desde != null) {
                predicados.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde));
            }
            if (hasta != null) {
                predicados.add(cb.lessThanOrEqualTo(root.get("fecha"), hasta));
            }
            if (estado != null) {
                predicados.add(cb.equal(root.get("estadoReserva"), estado));
            }
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
                predicados.add(cb.or(
                        cb.like(cb.lower(usuario.get("email")), patron),
                        cb.like(cb.lower(usuario.get("nombre")), patron),
                        cb.like(cb.lower(usuario.get("apellido")), patron),
                        cb.like(cb.lower(cancha.get("nombre")), patron)));
            }
            return cb.and(predicados.toArray(Predicate[]::new));
        };
    }
}
