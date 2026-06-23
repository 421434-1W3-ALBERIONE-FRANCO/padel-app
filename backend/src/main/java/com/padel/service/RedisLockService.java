package com.padel.service;

import java.time.LocalDate;
import java.time.LocalTime;

public interface RedisLockService {
    boolean acquireLock(Long canchaId, LocalDate fecha, LocalTime hora);
    void releaseLock(Long canchaId, LocalDate fecha, LocalTime hora);
    boolean hasLock(Long canchaId, LocalDate fecha, LocalTime hora);
}
