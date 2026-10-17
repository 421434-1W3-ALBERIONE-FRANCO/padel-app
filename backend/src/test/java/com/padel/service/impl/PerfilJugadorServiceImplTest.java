package com.padel.service.impl;

import com.padel.domain.entity.DisponibilidadJugador;
import com.padel.domain.entity.PerfilJugador;
import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.BloqueHorario;
import com.padel.domain.enums.CategoriaJugador;
import com.padel.domain.enums.PosicionJuego;
import com.padel.domain.enums.RolUsuario;
import com.padel.dto.request.BloqueDisponibleRequest;
import com.padel.dto.request.PerfilJugadorRequest;
import com.padel.dto.response.PerfilJugadorResponse;
import com.padel.exception.ResourceNotFoundException;
import com.padel.mapper.MatchMapper;
import com.padel.repository.PerfilJugadorRepository;
import com.padel.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerfilJugadorServiceImplTest {

    @Mock
    private PerfilJugadorRepository perfilJugadorRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Spy
    private MatchMapper matchMapper = new MatchMapper();

    @InjectMocks
    private PerfilJugadorServiceImpl perfilJugadorService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder().id(1L).nombre("Juan").apellido("Perez").email("juan@test.com").rol(RolUsuario.JUGADOR).activo(true).build();
        when(usuarioRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(usuario));
    }

    @Test
    void obtenerMiPerfil_sinPerfil_lanzaNotFound() {
        when(perfilJugadorRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> perfilJugadorService.obtenerMiPerfil("juan@test.com"));
    }

    @Test
    void guardarMiPerfil_creaElPerfilVisiblePorDefecto() {
        when(perfilJugadorRepository.findByUsuarioId(1L)).thenReturn(Optional.empty());
        when(perfilJugadorRepository.save(any(PerfilJugador.class))).thenAnswer(inv -> inv.getArgument(0));

        PerfilJugadorResponse response = perfilJugadorService.guardarMiPerfil(new PerfilJugadorRequest(
                CategoriaJugador.CUARTA, PosicionJuego.REVES, "  Juego de noche  ", null,
                List.of(new BloqueDisponibleRequest(DayOfWeek.FRIDAY, BloqueHorario.NOCHE),
                        new BloqueDisponibleRequest(DayOfWeek.MONDAY, BloqueHorario.NOCHE))), "juan@test.com");

        assertEquals(CategoriaJugador.CUARTA, response.categoria());
        assertTrue(response.visibleEnMatch());
        assertEquals("Juego de noche", response.bio());
        // La disponibilidad sale ordenada por día de la semana.
        assertEquals(DayOfWeek.MONDAY, response.disponibilidad().get(0).diaSemana());
        assertEquals(2, response.disponibilidad().size());
    }

    @Test
    void guardarMiPerfil_reemplazaLaDisponibilidadDelPerfilExistente() {
        PerfilJugador existente = PerfilJugador.builder().id(10L).usuario(usuario)
                .categoria(CategoriaJugador.QUINTA).posicion(PosicionJuego.DRIVE).visibleEnMatch(true)
                .disponibilidad(new HashSet<>(Set.of(new DisponibilidadJugador(DayOfWeek.SUNDAY, BloqueHorario.MANANA))))
                .build();
        when(perfilJugadorRepository.findByUsuarioId(1L)).thenReturn(Optional.of(existente));
        when(perfilJugadorRepository.save(any(PerfilJugador.class))).thenAnswer(inv -> inv.getArgument(0));

        PerfilJugadorResponse response = perfilJugadorService.guardarMiPerfil(new PerfilJugadorRequest(
                CategoriaJugador.CUARTA, PosicionJuego.INDISTINTO, " ", false,
                List.of(new BloqueDisponibleRequest(DayOfWeek.TUESDAY, BloqueHorario.TARDE))), "juan@test.com");

        assertEquals(10L, response.id());
        assertEquals(PosicionJuego.INDISTINTO, response.posicion());
        assertFalse(response.visibleEnMatch());
        assertNull(response.bio());
        assertEquals(Set.of(new DisponibilidadJugador(DayOfWeek.TUESDAY, BloqueHorario.TARDE)), existente.getDisponibilidad());
    }
}
