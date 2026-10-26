package com.padel.service.impl;

import com.padel.service.RedisLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Lock "blando" del horario mientras se paga. Es una optimización de UX: la garantía contra reservas
 * superpuestas está en la base (lock de la cancha + restricción EXCLUDE). Por eso, si Redis no responde,
 * se sigue sin él en lugar de dejar al club sin poder reservar ni ver la disponibilidad.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockServiceImpl implements RedisLockService {

    private static final Duration TTL = Duration.ofSeconds(600);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean acquireLock(Long canchaId, LocalDate fecha, LocalTime hora) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(buildKey(canchaId, fecha, hora), duenoDelLock(), TTL);
            return ok == null || ok;
        } catch (DataAccessException e) {
            log.warn("Redis no disponible al tomar el lock del horario; se continúa con el control de la base: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public void releaseLock(Long canchaId, LocalDate fecha, LocalTime hora) {
        try {
            redisTemplate.delete(buildKey(canchaId, fecha, hora));
        } catch (DataAccessException e) {
            log.warn("Redis no disponible al liberar el lock del horario (vence solo a los {} s): {}", TTL.toSeconds(), e.getMessage());
        }
    }

    @Override
    public boolean hasLock(Long canchaId, LocalDate fecha, LocalTime hora) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(canchaId, fecha, hora)));
        } catch (DataAccessException e) {
            return false;
        }
    }

    private String buildKey(Long canchaId, LocalDate fecha, LocalTime hora) {
        return String.format("padelapp:lock:cancha:%d:fecha:%s:hora:%02d:%02d", canchaId, fecha, hora.getHour(), hora.getMinute());
    }

    // Antes se buscaba el usuario en la base solo para guardar su id como valor del lock; alcanza con el nombre
    // del principal (el valor no se usa para decidir nada).
    private String duenoDelLock() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || auth.getName() == null ? "SYSTEM" : String.valueOf(auth.getName().hashCode());
    }
}
