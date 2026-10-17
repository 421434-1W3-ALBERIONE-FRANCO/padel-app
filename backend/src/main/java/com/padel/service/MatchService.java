package com.padel.service;

import com.padel.dto.request.ReservaMatchRequest;
import com.padel.dto.request.SwipeRequest;
import com.padel.dto.response.CandidatoMatchResponse;
import com.padel.dto.response.MatchParejaResponse;
import com.padel.dto.response.ReservaResponse;
import com.padel.dto.response.SwipeResponse;
import com.padel.dto.response.TurnoSugeridoResponse;

import java.util.List;

public interface MatchService {
    List<CandidatoMatchResponse> obtenerCandidatos(String email, int limite);
    SwipeResponse registrarSwipe(SwipeRequest request, String email);
    List<MatchParejaResponse> obtenerMisParejas(String email);
    List<TurnoSugeridoResponse> sugerirTurnos(Long matchId, String email, int dias);
    ReservaResponse reservarJuntos(Long matchId, ReservaMatchRequest request, String email);
}
