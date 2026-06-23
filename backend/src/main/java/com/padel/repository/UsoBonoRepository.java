package com.padel.repository;

import com.padel.domain.entity.UsoBono;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsoBonoRepository extends JpaRepository<UsoBono, Long> {
    List<UsoBono> findByBonoId(Long bonoId);
    List<UsoBono> findByReservaId(Long reservaId);
}
