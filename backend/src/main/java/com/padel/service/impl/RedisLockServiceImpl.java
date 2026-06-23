package com.padel.service.impl;

import com.padel.repository.UsuarioRepository;
import com.padel.service.RedisLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
public class RedisLockServiceImpl implements RedisLockService {

    private final StringRedisTemplate redisTemplate;
    private final UsuarioRepository usuarioRepository;

    @Override
    public boolean acquireLock(Long canchaId, LocalDate fecha, LocalTime hora) {
        String key = buildKey(canchaId, fecha, hora);
        String userId = getUserIdOrSystem();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, userId, Duration.ofSeconds(600));
        return success != null && success;
    }

    @Override
    public void releaseLock(Long canchaId, LocalDate fecha, LocalTime hora) {
        String key = buildKey(canchaId, fecha, hora);
        redisTemplate.delete(key);
    }

    @Override
    public boolean hasLock(Long canchaId, LocalDate fecha, LocalTime hora) {
        String key = buildKey(canchaId, fecha, hora);
        Boolean exists = redisTemplate.hasKey(key);
        return exists != null && exists;
    }

    private String buildKey(Long canchaId, LocalDate fecha, LocalTime hora) {
        String fechaStr = fecha.toString();
        String horaStr = String.format("%02d:%02d", hora.getHour(), hora.getMinute());
        return String.format("padelapp:lock:cancha:%d:fecha:%s:hora:%s", canchaId, fechaStr, horaStr);
    }

    private String getUserIdOrSystem() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return "SYSTEM";
        }
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        if (email == null || "anonymousUser".equals(email)) {
            return "SYSTEM";
        }
        return usuarioRepository.findByEmail(email)
                .map(u -> u.getId().toString())
                .orElse("SYSTEM");
    }
}
