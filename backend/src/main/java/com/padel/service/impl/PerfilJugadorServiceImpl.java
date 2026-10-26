package com.padel.service.impl;

import com.padel.domain.entity.DisponibilidadJugador;
import com.padel.domain.entity.PerfilJugador;
import com.padel.domain.entity.Usuario;
import com.padel.dto.request.PerfilJugadorRequest;
import com.padel.dto.response.PerfilJugadorResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.MatchMapper;
import com.padel.repository.PerfilJugadorRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.service.PerfilJugadorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PerfilJugadorServiceImpl implements PerfilJugadorService {

    private final PerfilJugadorRepository perfilJugadorRepository;
    private final UsuarioRepository usuarioRepository;
    private final MatchMapper matchMapper;

    @Override
    @Transactional(readOnly = true)
    public PerfilJugadorResponse obtenerMiPerfil(String email) {
        Usuario usuario = obtenerUsuario(email);
        return perfilJugadorRepository.findByUsuarioId(usuario.getId())
                .map(matchMapper::toPerfilResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Todavía no completaste tu perfil de jugador"));
    }

    @Override
    public PerfilJugadorResponse guardarMiPerfil(PerfilJugadorRequest request, String email) {
        Usuario usuario = obtenerUsuario(email);
        PerfilJugador perfil = perfilJugadorRepository.findByUsuarioId(usuario.getId())
                .orElseGet(() -> PerfilJugador.builder().usuario(usuario).build());

        perfil.setCategoria(request.categoria());
        perfil.setPosicion(request.posicion());
        perfil.setBio(request.bio() == null || request.bio().isBlank() ? null : request.bio().trim());
        perfil.setVisibleEnMatch(request.visibleEnMatch() == null || request.visibleEnMatch());
        perfil.getDisponibilidad().clear();
        request.disponibilidad().forEach(b ->
                perfil.getDisponibilidad().add(new DisponibilidadJugador(b.diaSemana(), b.bloque())));

        PerfilJugador guardado = perfilJugadorRepository.save(perfil);
        log.info("Perfil de jugador ID {} guardado ({} horarios disponibles)", guardado.getId(), guardado.getDisponibilidad().size());
        return matchMapper.toPerfilResponse(guardado);
    }

    private Usuario obtenerUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }
}
