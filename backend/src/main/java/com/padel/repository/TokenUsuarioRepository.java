package com.padel.repository;

import com.padel.domain.entity.TokenUsuario;
import com.padel.domain.enums.TipoTokenUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenUsuarioRepository extends JpaRepository<TokenUsuario, Long> {

    Optional<TokenUsuario> findByTokenHashAndTipoAndUsadoFalse(String tokenHash, TipoTokenUsuario tipo);

    // Al emitir un token nuevo (o usar uno) los anteriores del mismo tipo dejan de servir: antes quedaban
    // varios enlaces de recuperación válidos a la vez.
    @Modifying
    @Query("UPDATE TokenUsuario t SET t.usado = true WHERE t.usuario.id = :usuarioId AND t.tipo = :tipo AND t.usado = false")
    int invalidarPendientes(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoTokenUsuario tipo);
}
