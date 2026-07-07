import { Injectable, NgZone, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';

const INACTIVITY_LIMIT_MS = 15 * 60 * 1000; // 15 min sin actividad
const WARNING_DURATION_S = 60; // aviso con cuenta regresiva de 60s antes de cerrar sesión
const ACTIVITY_EVENTS = ['mousemove', 'keydown', 'click', 'scroll', 'touchstart'] as const;

@Injectable({ providedIn: 'root' })
export class InactivityService {
  private authService = inject(AuthService);
  private zone = inject(NgZone);

  showWarning = signal(false);
  secondsRemaining = signal(WARNING_DURATION_S);

  private inactivityTimer?: ReturnType<typeof setTimeout>;
  private countdownTimer?: ReturnType<typeof setInterval>;
  private started = false;
  private readonly onActivity = () => {
    if (!this.showWarning()) this.resetInactivityTimer();
  };

  start(): void {
    if (this.started) return;
    this.started = true;
    this.zone.runOutsideAngular(() => {
      ACTIVITY_EVENTS.forEach(evt => window.addEventListener(evt, this.onActivity, { passive: true }));
    });
    this.resetInactivityTimer();
  }

  stop(): void {
    this.started = false;
    ACTIVITY_EVENTS.forEach(evt => window.removeEventListener(evt, this.onActivity));
    clearTimeout(this.inactivityTimer);
    clearInterval(this.countdownTimer);
    this.showWarning.set(false);
  }

  seguirConectado(): void {
    this.resetInactivityTimer();
  }

  private resetInactivityTimer(): void {
    clearTimeout(this.inactivityTimer);
    clearInterval(this.countdownTimer);
    this.showWarning.set(false);
    this.secondsRemaining.set(WARNING_DURATION_S);
    this.inactivityTimer = setTimeout(
      () => this.zone.run(() => this.beginCountdown()),
      INACTIVITY_LIMIT_MS - WARNING_DURATION_S * 1000
    );
  }

  private beginCountdown(): void {
    this.showWarning.set(true);
    this.secondsRemaining.set(WARNING_DURATION_S);
    this.countdownTimer = setInterval(() => {
      const remaining = this.secondsRemaining() - 1;
      this.secondsRemaining.set(remaining);
      if (remaining <= 0) {
        clearInterval(this.countdownTimer);
        this.stop();
        this.authService.logout();
      }
    }, 1000);
  }
}
