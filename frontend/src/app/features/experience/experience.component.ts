import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { PaddleSceneComponent } from './paddle-scene.component';
import { LiquidImageComponent } from './liquid-image.component';

gsap.registerPlugin(ScrollTrigger);

@Component({
  selector: 'app-experience',
  standalone: true,
  imports: [CommonModule, RouterLink, PaddleSceneComponent, LiquidImageComponent],
  templateUrl: './experience.component.html'
})
export class ExperienceComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvasHost', { static: true }) private canvasHost!: ElementRef<HTMLDivElement>;
  @ViewChild('loaderEl') private loaderEl?: ElementRef<HTMLDivElement>;
  @ViewChild('counterEl') private counterEl?: ElementRef<HTMLSpanElement>;
  @ViewChild('barEl') private barEl?: ElementRef<HTMLDivElement>;
  @ViewChild(PaddleSceneComponent) private paddleScene!: PaddleSceneComponent;

  showLoader = true;

  private scrollTrigger?: ScrollTrigger;
  private introTimeline?: gsap.core.Timeline;

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
  }

  ngOnDestroy() {
    this.scrollTrigger?.kill();
    this.introTimeline?.kill();
  }

  @HostListener('window:mousemove', ['$event'])
  onMouseMove(event: MouseEvent) {
    const nx = (event.clientX / window.innerWidth) * 2 - 1;
    const ny = (event.clientY / window.innerHeight) * 2 - 1;
    this.paddleScene?.setPointer(nx, ny);
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
