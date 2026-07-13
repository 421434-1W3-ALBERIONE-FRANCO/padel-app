import { AfterViewInit, Component, ElementRef, HostListener, Input, OnDestroy, ViewChild } from '@angular/core';
import * as THREE from 'three';

const VERTEX_SHADER = `
  varying vec2 vUv;
  void main() {
    vUv = uv;
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`;

// Distorsión "líquida": desplaza el UV en un anillo que se aleja del mouse (como
// una onda en el agua) y separa levemente los canales R/G/B cerca del cursor
// (aberración cromática) para que se sienta vidrio/líquido, no solo un desplazamiento.
const FRAGMENT_SHADER = `
  uniform sampler2D uTexture;
  uniform vec2 uMouse;
  uniform float uHover;
  uniform float uTime;
  varying vec2 vUv;

  void main() {
    vec2 uv = vUv;
    vec2 toMouse = uv - uMouse;
    float dist = length(toMouse);
    float falloff = smoothstep(0.55, 0.0, dist);
    vec2 dir = toMouse / (dist + 0.0001);

    float ripple = sin(dist * 24.0 - uTime * 3.2) * 0.018 * uHover * falloff;
    vec2 distortedUv = uv + dir * ripple;

    float aberration = 0.008 * uHover * falloff;
    float r = texture2D(uTexture, distortedUv + dir * aberration).r;
    float g = texture2D(uTexture, distortedUv).g;
    float b = texture2D(uTexture, distortedUv - dir * aberration).b;

    gl_FragColor = vec4(r, g, b, 1.0);
  }
`;

@Component({
  selector: 'app-liquid-image',
  standalone: true,
  template: `<canvas #canvas class="w-full h-full block cursor-pointer"></canvas>`,
  styles: [`:host { display: block; width: 100%; height: 100%; }`]
})
export class LiquidImageComponent implements AfterViewInit, OnDestroy {
  @Input() title = '';
  @Input() colorFrom = '#52c41a';
  @Input() colorTo = '#06b6d4';
  @Input() glyph = '●';

  @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;

  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.OrthographicCamera;
  private material!: THREE.ShaderMaterial;
  private animationFrameId = 0;

  private targetMouse = { x: 0.5, y: 0.5 };
  private currentMouse = { x: 0.5, y: 0.5 };
  private targetHover = 0;
  private currentHover = 0;

  ngAfterViewInit() {
    this.initScene();
    this.animate();
  }

  ngOnDestroy() {
    cancelAnimationFrame(this.animationFrameId);
    this.material?.dispose();
    (this.material?.uniforms?.['uTexture']?.value as THREE.Texture | undefined)?.dispose();
    this.renderer?.dispose();
  }

  @HostListener('window:resize')
  onResize() {
    if (!this.renderer) return;
    const canvas = this.canvasRef.nativeElement;
    this.renderer.setSize(canvas.clientWidth, canvas.clientHeight, false);
  }

  @HostListener('mousemove', ['$event'])
  onMouseMove(event: MouseEvent) {
    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    this.targetMouse.x = (event.clientX - rect.left) / rect.width;
    this.targetMouse.y = 1 - (event.clientY - rect.top) / rect.height;
  }

  @HostListener('mouseenter')
  onMouseEnter() {
    this.targetHover = 1;
  }

  @HostListener('mouseleave')
  onMouseLeave() {
    this.targetHover = 0;
  }

  private initScene() {
    const canvas = this.canvasRef.nativeElement;
    this.scene = new THREE.Scene();
    this.camera = new THREE.OrthographicCamera(-0.5, 0.5, 0.5, -0.5, 0.1, 10);
    this.camera.position.z = 1;

    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.setSize(canvas.clientWidth, canvas.clientHeight, false);

    const texture = new THREE.CanvasTexture(this.createTileTexture());
    texture.colorSpace = THREE.SRGBColorSpace;

    this.material = new THREE.ShaderMaterial({
      vertexShader: VERTEX_SHADER,
      fragmentShader: FRAGMENT_SHADER,
      uniforms: {
        uTexture: { value: texture },
        uMouse: { value: new THREE.Vector2(0.5, 0.5) },
        uHover: { value: 0 },
        uTime: { value: 0 }
      }
    });

    const plane = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), this.material);
    this.scene.add(plane);
  }

  private createTileTexture(): HTMLCanvasElement {
    const size = 512;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;

    const gradient = ctx.createLinearGradient(0, 0, size, size);
    gradient.addColorStop(0, '#0d0d10');
    gradient.addColorStop(1, '#050507');
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, size, size);

    ctx.save();
    ctx.translate(size / 2, size / 2);
    const glow = ctx.createRadialGradient(0, 0, 10, 0, 0, size * 0.42);
    glow.addColorStop(0, this.colorFrom);
    glow.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.globalAlpha = 0.35;
    ctx.fillStyle = glow;
    ctx.beginPath();
    ctx.arc(0, 0, size * 0.42, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = 1;

    const iconGradient = ctx.createLinearGradient(-size * 0.18, -size * 0.18, size * 0.18, size * 0.18);
    iconGradient.addColorStop(0, this.colorFrom);
    iconGradient.addColorStop(1, this.colorTo);
    ctx.fillStyle = iconGradient;
    ctx.font = `${size * 0.26}px sans-serif`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(this.glyph, 0, -size * 0.04);
    ctx.restore();

    ctx.fillStyle = 'rgba(255,255,255,0.92)';
    ctx.font = `700 ${size * 0.062}px sans-serif`;
    ctx.textAlign = 'center';
    ctx.fillText(this.title, size / 2, size * 0.82);

    return canvas;
  }

  private animate() {
    this.animationFrameId = requestAnimationFrame(() => this.animate());

    this.currentMouse.x += (this.targetMouse.x - this.currentMouse.x) * 0.12;
    this.currentMouse.y += (this.targetMouse.y - this.currentMouse.y) * 0.12;
    this.currentHover += (this.targetHover - this.currentHover) * 0.08;

    const uniforms = this.material.uniforms;
    (uniforms['uMouse'].value as THREE.Vector2).set(this.currentMouse.x, this.currentMouse.y);
    uniforms['uHover'].value = this.currentHover;
    uniforms['uTime'].value = performance.now() * 0.001;

    this.renderer.render(this.scene, this.camera);
  }
}
