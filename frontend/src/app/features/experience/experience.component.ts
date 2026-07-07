import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, ViewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';
import { PaddleSceneComponent } from './paddle-scene.component';

gsap.registerPlugin(ScrollTrigger);

@Component({
  selector: 'app-experience',
  standalone: true,
  imports: [RouterLink, PaddleSceneComponent],
  templateUrl: './experience.component.html'
})
export class ExperienceComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvasHost', { static: true }) private canvasHost!: ElementRef<HTMLDivElement>;
  @ViewChild(PaddleSceneComponent) private paddleScene!: PaddleSceneComponent;

  private scrollTrigger?: ScrollTrigger;

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
  }

  ngOnDestroy() {
    this.scrollTrigger?.kill();
  }

  @HostListener('window:mousemove', ['$event'])
  onMouseMove(event: MouseEvent) {
    const nx = (event.clientX / window.innerWidth) * 2 - 1;
    const ny = (event.clientY / window.innerHeight) * 2 - 1;
    this.paddleScene?.setPointer(nx, ny);
  }
}
