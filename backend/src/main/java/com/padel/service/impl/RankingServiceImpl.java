package com.padel.service.impl;

import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.CategoriaJugador;
import com.padel.dto.response.RankingJugadorResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.RankingJugadorMapper;
import com.padel.repository.RankingJugadorRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RankingServiceImpl implements RankingService {

    private final RankingJugadorRepository rankingJugadorRepository;
    private final UsuarioRepository usuarioRepository;
    private final RankingJugadorMapper rankingJugadorMapper;

    @Override
    public List<RankingJugadorResponse> obtenerRankingPorCategoria(CategoriaJugador categoria) {
        log.info("Obteniendo ranking de la categoría {}", categoria);
        return rankingJugadorRepository.findByCategoriaOrderByPuntosDescPartidosGanadosDesc(categoria).stream()
                .map(rankingJugadorMapper::toResponse)
                .toList();
    }

    @Override
    public RankingJugadorResponse obtenerMiPosicion(CategoriaJugador categoria, String usuarioEmail) {
        Usuario usuario = usuarioRepository.findByEmail(usuarioEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + usuarioEmail));

        return rankingJugadorRepository.findByJugadorIdAndCategoria(usuario.getId(), categoria)
                .map(rankingJugadorMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Todavía no tenés ranking en la categoría " + categoria));
    }
}
