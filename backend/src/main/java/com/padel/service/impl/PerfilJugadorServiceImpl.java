package com.padel.service.impl;

import com.padel.domain.entity.DisponibilidadJugador;
import com.padel.domain.entity.FotoPerfil;
import com.padel.domain.entity.PerfilJugador;
import com.padel.domain.entity.Usuario;
import com.padel.dto.request.PerfilJugadorRequest;
import com.padel.dto.response.FotoPerfilArchivo;
import com.padel.dto.response.PerfilJugadorResponse;
import com.padel.exception.EstadoInvalidoException;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.MatchMapper;
import com.padel.repository.FotoPerfilRepository;
import com.padel.repository.MatchParejaRepository;
import com.padel.repository.PerfilJugadorRepository;
import com.padel.repository.UsuarioRepository;
import com.padel.security.ControlAcceso;
import com.padel.service.PerfilJugadorService;
import com.padel.util.ImagenPerfilProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PerfilJugadorServiceImpl implements PerfilJugadorService {

    private final PerfilJugadorRepository perfilJugadorRepository;
    private final UsuarioRepository usuarioRepository;
    private final MatchMapper matchMapper;
    private final FotoPerfilRepository fotoPerfilRepository;
    private final MatchParejaRepository matchParejaRepository;
    private final ImagenPerfilProcessor imagenPerfilProcessor;
    private final ControlAcceso controlAcceso;

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

    @Override
    public PerfilJugadorResponse guardarMiFoto(byte[] imagen, String email) {
        PerfilJugador perfil = perfilExistente(obtenerUsuario(email));
        byte[] normalizada = imagenPerfilProcessor.normalizar(imagen);
        LocalDateTime ahora = LocalDateTime.now();

        FotoPerfil foto = fotoPerfilRepository.findById(perfil.getId())
                .orElseGet(() -> FotoPerfil.builder().perfilId(perfil.getId()).build());
        foto.setContenido(normalizada);
        foto.setTipoContenido(ImagenPerfilProcessor.TIPO);
        foto.setActualizadaEn(ahora);
        fotoPerfilRepository.save(foto);

        perfil.setFotoActualizadaEn(ahora);
        log.info("Foto de perfil del perfil ID {} actualizada ({} bytes)", perfil.getId(), normalizada.length);
        return matchMapper.toPerfilResponse(perfilJugadorRepository.save(perfil));
    }

    @Override
    public PerfilJugadorResponse eliminarMiFoto(String email) {
        PerfilJugador perfil = perfilExistente(obtenerUsuario(email));
        fotoPerfilRepository.findById(perfil.getId()).ifPresent(fotoPerfilRepository::delete);
        perfil.setFotoActualizadaEn(null);
        return matchMapper.toPerfilResponse(perfilJugadorRepository.save(perfil));
    }

    /**
     * La foto la ven el propio jugador, el staff, quienes lo pueden encontrar en el mazo (perfil visible en el
     * match) y sus matches aunque después haya ocultado el perfil. Para el resto, 404: no confirma que exista.
     */
    @Override
    @Transactional(readOnly = true)
    public FotoPerfilArchivo obtenerFoto(Long usuarioId, String emailQuienConsulta) {
        Usuario quienConsulta = obtenerUsuario(emailQuienConsulta);
        PerfilJugador perfil = perfilJugadorRepository.findByUsuarioId(usuarioId)
                .filter(p -> p.getFotoActualizadaEn() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Foto no encontrada"));

        boolean propia = quienConsulta.getId().equals(usuarioId);
        boolean puedeVerla = propia
                || controlAcceso.esStaff(quienConsulta)
                || (perfil.isVisibleEnMatch() && perfil.getUsuario().isActivo())
                || hayMatchEntre(quienConsulta.getId(), usuarioId);
        if (!puedeVerla) {
            throw new ResourceNotFoundException("Foto no encontrada");
        }

        FotoPerfil foto = fotoPerfilRepository.findById(perfil.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Foto no encontrada"));
        return new FotoPerfilArchivo(foto.getContenido(), foto.getTipoContenido(), MatchMapper.fotoVersion(perfil));
    }

    private boolean hayMatchEntre(Long a, Long b) {
        return matchParejaRepository.findByJugador1IdAndJugador2Id(Math.min(a, b), Math.max(a, b)).isPresent();
    }

    // La foto se guarda junto al perfil: un usuario nuevo primero completa el perfil (el front la sube después).
    private PerfilJugador perfilExistente(Usuario usuario) {
        return perfilJugadorRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new EstadoInvalidoException("Primero guardá tu perfil de jugador"));
    }

    private Usuario obtenerUsuario(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con email: " + email));
    }
}
