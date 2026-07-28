import { Component, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './core/services/auth.service';
import { InactivityService } from './core/services/inactivity.service';
import { ToastContainerComponent } from './shared/components/toast/toast-container.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, ToastContainerComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'padel-frontend';

  private authService = inject(AuthService);
  inactivity = inject(InactivityService);

  constructor() {
    effect(() => {
      if (this.authService.isAuthenticated()) {
        this.inactivity.start();
      } else {
        this.inactivity.stop();
      }
    }, { allowSignalWrites: true });
  }

  seguirConectado(): void {
    this.inactivity.seguirConectado();
  }
}
