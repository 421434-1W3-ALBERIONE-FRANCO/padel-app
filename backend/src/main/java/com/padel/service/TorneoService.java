package com.padel.service;

import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.EstadoTorneo;
import com.padel.domain.enums.TipoTorneo;
import com.padel.dto.request.InscripcionTorneoRequest;
import com.padel.dto.request.ResultadoPartidoRequest;
import com.padel.dto.request.TorneoRequest;
import com.padel.dto.response.InscripcionTorneoResponse;
import com.padel.dto.response.PartidoTorneoResponse;
import com.padel.dto.response.TorneoResponse;

import java.util.List;

public interface TorneoService {
    TorneoResponse crear(TorneoRequest request);
    TorneoResponse actualizar(Long id, TorneoRequest request);
    List<TorneoResponse> buscar(EstadoTorneo estado, CategoriaJugador categoria, TipoTorneo tipo);
    TorneoResponse obtenerPorId(Long id);
    void cancelar(Long id);

    InscripcionTorneoResponse inscribirPareja(Long torneoId, InscripcionTorneoRequest request, String usuarioEmail);
    List<InscripcionTorneoResponse> listarInscripciones(Long torneoId);
    void cancelarInscripcion(Long torneoId, Long inscripcionId, String usuarioEmail);

    List<PartidoTorneoResponse> generarFixture(Long torneoId);
    List<PartidoTorneoResponse> listarPartidos(Long torneoId);
    PartidoTorneoResponse cargarResultado(Long torneoId, Long partidoId, ResultadoPartidoRequest request);
    List<PartidoTorneoResponse> generarSiguienteRonda(Long torneoId);
}
