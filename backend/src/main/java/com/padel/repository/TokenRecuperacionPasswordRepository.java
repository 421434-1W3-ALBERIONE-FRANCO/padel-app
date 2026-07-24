package com.padel.repository;

import com.padel.domain.entity.TokenRecuperacionPassword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenRecuperacionPasswordRepository extends JpaRepository<TokenRecuperacionPassword, Long> {
    Optional<TokenRecuperacionPassword> findByTokenHashAndUsadoFalse(String tokenHash);
}
