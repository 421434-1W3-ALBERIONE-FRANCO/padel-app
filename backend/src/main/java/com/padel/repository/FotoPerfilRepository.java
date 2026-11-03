package com.padel.repository;

import com.padel.domain.entity.FotoPerfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FotoPerfilRepository extends JpaRepository<FotoPerfil, Long> {
}
