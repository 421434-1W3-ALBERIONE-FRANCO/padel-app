import { AfterViewInit, Component, ElementRef, HostListener, inject, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { PaddleSceneComponent } from './paddle-scene.component';
import { EcosystemCarouselComponent } from './ecosystem-carousel.component';

gsap.registerPlugin(ScrollTrigger);

@Component({
  selector: 'app-experience',
  standalone: true,
  imports: [CommonModule, PaddleSceneComponent, EcosystemCarouselComponent],
  templateUrl: './experience.component.html'
})
export class ExperienceComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvasHost', { static: true }) private canvasHost!: ElementRef<HTMLDivElement>;
  @ViewChild('loaderEl') private loaderEl?: ElementRef<HTMLDivElement>;
  @ViewChild('counterEl') private counterEl?: ElementRef<HTMLSpanElement>;
  @ViewChild('barEl') private barEl?: ElementRef<HTMLDivElement>;
  @ViewChild('transitionEl') private transitionEl?: ElementRef<HTMLDivElement>;
  @ViewChild(PaddleSceneComponent) private paddleScene!: PaddleSceneComponent;

  private router = inject(Router);

  showLoader = true;

  private scrollTrigger?: ScrollTrigger;
  private skewTrigger?: ScrollTrigger;
  private introTimeline?: gsap.core.Timeline;
  private ecoTriggers: ScrollTrigger[] = [];

  ngAfterViewInit() {
    // El canvas es fixed (background), así que no necesita pin: solo medimos el
    // progreso de scroll de todo el documento y lo pasamos a la escena 3D.
    this.scrollTrigger = ScrollTrigger.create({
      trigger: this.canvasHost.nativeElement.parentElement!,
      start: 'top top',
      end: 'bottom bottom',
      scrub: true,
      onUpdate: (self) => this.paddleScene.setProgress(self.progress)
    });

    this.runIntro();
    this.runSkewOnScroll();
    this.runEcoReveal();
  }

  ngOnDestroy() {
    this.scrollTrigger?.kill();
    this.skewTrigger?.kill();
    this.introTimeline?.kill();
    this.ecoTriggers.forEach(t => t.kill());
  }

  @HostListener('window:mousemove', ['$event'])
  onMouseMove(event: MouseEvent) {
    const nx = (event.clientX / window.innerWidth) * 2 - 1;
    const ny = (event.clientY / window.innerHeight) * 2 - 1;
    this.paddleScene?.setPointer(nx, ny);
  }

  /** Deformación de texto al scroll (estilo Lusion): los títulos se "estiran"
   * (skew) en proporción a la velocidad de scroll y vuelven a su lugar apenas
   * el scroll frena, en vez de quedar siempre estáticos. */
  private runSkewOnScroll() {
    const targets = '.skew-text';
    const clamp = gsap.utils.clamp(-14, 14);
    this.skewTrigger = ScrollTrigger.create({
      trigger: this.canvasHost.nativeElement.parentElement!,
      start: 'top top',
      end: 'bottom bottom',
      onUpdate: (self) => {
        const skew = clamp(self.getVelocity() / -250);
        gsap.to(targets, { skewX: skew, duration: 0.5, ease: 'power3.out', overwrite: true });
      },
      onLeave: () => gsap.to(targets, { skewX: 0, duration: 0.4 }),
      onLeaveBack: () => gsap.to(targets, { skewX: 0, duration: 0.4 })
    });
  }

  /** Tarjetas del ecosistema (Reservas/Torneos/Ranking/Comunidad): entran de
   * opacidad 0 a 1 con una leve escala y stagger apenas la sección es visible,
   * en vez de aparecer ya renderizadas de golpe con el resto de la página. */
  private runEcoReveal() {
    gsap.set('.eco-reveal', { opacity: 0, y: 24, scale: 0.94 });
    this.ecoTriggers = ScrollTrigger.batch('.eco-reveal', {
      start: 'top 88%',
      onEnter: (batch) => gsap.to(batch, {
        opacity: 1, y: 0, scale: 1, duration: 0.7, stagger: 0.12, ease: 'power3.out'
      })
    });
  }

  /** Transición de salida: en vez de cortar directo a otra ruta, hace un fade
   * corto y recién ahí navega, para no perder la continuidad de la experiencia. */
  navigateAway(path: string, event: Event) {
    event.preventDefault();
    this.fadeAndNavigate(path);
  }

  /** Click en el carrusel del ecosistema: la ruta destino ya está protegida por
   * authGuard (y sus roles, si los tuviera), así que acá solo se dispara la
   * misma transición de salida — sin usuario logueado termina en el login,
   * logueado entra directo sin importar si es JUGADOR o ADMIN. */
  onEcosystemNavigate(path: string) {
    this.fadeAndNavigate(path);
  }

  private fadeAndNavigate(path: string) {
    const overlay = this.transitionEl?.nativeElement;
    if (!overlay) {
      this.router.navigate([path]);
      return;
    }

    let navigated = false;
    const goNow = () => {
      if (navigated) return;
      navigated = true;
      this.router.navigate([path]);
    };

    gsap.to(overlay, { opacity: 1, duration: 0.45, ease: 'power2.inOut', onComplete: goNow });
    // Salvaguarda: la animación depende de requestAnimationFrame, que el navegador
    // puede pausar (pestaña en segundo plano, throttling). La navegación nunca
    // debería quedar colgada esperando un tick de rAF que no llega.
    setTimeout(goNow, 700);
  }

  /** Loader tipo Lusion: contador 0->100, luego wipe hacia arriba y entrada del hero. */
  private runIntro() {
    const loader = this.loaderEl?.nativeElement;
    const counter = this.counterEl?.nativeElement;
    const bar = this.barEl?.nativeElement;
    if (!loader || !counter || !bar) return;

    const progress = { value: 0 };
    this.introTimeline = gsap.timeline()
      .to(progress, {
        value: 100,
        duration: 1.6,
        ease: 'power2.inOut',
        onUpdate: () => {
          counter.textContent = `${Math.round(progress.value)}%`;
          bar.style.width = `${progress.value}%`;
        }
      })
      .to(loader, {
        yPercent: -100,
        duration: 0.9,
        ease: 'power4.inOut',
        onComplete: () => { this.showLoader = false; }
      }, '+=0.15')
      .fromTo(
        '.hero-reveal',
        { y: 40, opacity: 0 },
        { y: 0, opacity: 1, duration: 0.9, stagger: 0.08, ease: 'power3.out' },
        '-=0.55'
      );
  }
}
