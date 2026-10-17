package com.padel.mapper;

import com.padel.domain.entity.DisponibilidadJugador;
import com.padel.domain.entity.MatchPareja;
import com.padel.domain.entity.PerfilJugador;
import com.padel.domain.entity.Usuario;
import com.padel.dto.response.BloqueDisponibleResponse;
import com.padel.dto.response.CandidatoMatchResponse;
import com.padel.dto.response.MatchParejaResponse;
import com.padel.dto.response.PerfilJugadorResponse;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

@Component
public class MatchMapper {

    private static final Comparator<DisponibilidadJugador> ORDEN_SEMANAL =
            Comparator.comparing(DisponibilidadJugador::getDiaSemana).thenComparing(DisponibilidadJugador::getBloque);

    public PerfilJugadorResponse toPerfilResponse(PerfilJugador perfil) {
        return new PerfilJugadorResponse(
                perfil.getId(),
                perfil.getUsuario().getId(),
                perfil.getCategoria(),
                perfil.getPosicion(),
                perfil.getBio(),
                perfil.isVisibleEnMatch(),
                toBloques(perfil.getDisponibilidad())
        );
    }

    public CandidatoMatchResponse toCandidato(PerfilJugador perfil, int puntosRanking, int afinidad,
                                              Collection<DisponibilidadJugador> coincidencias) {
        Usuario usuario = perfil.getUsuario();
        return new CandidatoMatchResponse(
                usuario.getId(),
                nombreAbreviado(usuario),
                iniciales(usuario),
                perfil.getCategoria(),
                perfil.getPosicion(),
                perfil.getBio(),
                puntosRanking,
                afinidad,
                toBloques(perfil.getDisponibilidad()),
                toBloques(coincidencias)
        );
    }

    /** @param perfilCompanero puede ser null si el compañero borró o nunca completó su perfil. */
    public MatchParejaResponse toPareja(MatchPareja match, Usuario companero, PerfilJugador perfilCompanero,
                                        Collection<DisponibilidadJugador> coincidencias) {
        return new MatchParejaResponse(
                match.getId(),
                companero.getId(),
                companero.getNombre() + " " + companero.getApellido(),
                iniciales(companero),
                perfilCompanero != null ? perfilCompanero.getCategoria() : null,
                perfilCompanero != null ? perfilCompanero.getPosicion() : null,
                companero.getEmail(),
                companero.getTelefono(),
                toBloques(coincidencias),
                match.getCreatedAt()
        );
    }

    public List<BloqueDisponibleResponse> toBloques(Collection<DisponibilidadJugador> bloques) {
        return bloques.stream()
                .sorted(ORDEN_SEMANAL)
                .map(b -> new BloqueDisponibleResponse(b.getDiaSemana(), b.getBloque()))
                .toList();
    }

    private String nombreAbreviado(Usuario usuario) {
        String apellido = usuario.getApellido();
        return apellido == null || apellido.isBlank()
                ? usuario.getNombre()
                : usuario.getNombre() + " " + apellido.charAt(0) + ".";
    }

    private String iniciales(Usuario usuario) {
        String nombre = usuario.getNombre() == null || usuario.getNombre().isBlank() ? "?" : usuario.getNombre().substring(0, 1);
        String apellido = usuario.getApellido() == null || usuario.getApellido().isBlank() ? "" : usuario.getApellido().substring(0, 1);
        return (nombre + apellido).toUpperCase();
    }
}
