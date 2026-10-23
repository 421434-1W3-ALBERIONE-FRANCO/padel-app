package com.padel.security;

import com.padel.repository.UsuarioRepository;
import com.padel.util.EmailUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    /** Login: el email se normaliza igual que al registrarse. */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return usuarioRepository.findByEmail(EmailUtil.normalizar(username))
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    /** Requests autenticados: el JWT identifica al usuario por id. */
    public Optional<CustomUserDetails> cargarPorId(Long id) {
        return usuarioRepository.findById(id).map(CustomUserDetails::new);
    }
}
