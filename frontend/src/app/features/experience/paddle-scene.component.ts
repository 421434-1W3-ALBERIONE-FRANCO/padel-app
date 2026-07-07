import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, ViewChild } from '@angular/core';
import * as THREE from 'three';

/**
 * Cámara "shots" a lo largo del scroll. t va de 0 a 1 y se interpola linealmente
 * entre keyframes consecutivos. Cada shot define dónde está la cámara, hacia dónde
 * mira y cómo está orientada la paleta.
 */
interface Shot {
  t: number;
  cameraPos: THREE.Vector3;
  lookAt: THREE.Vector3;
  paddleRot: THREE.Euler;
  paddlePos: THREE.Vector3;
}

@Component({
  selector: 'app-paddle-scene',
  standalone: true,
  template: `<canvas #canvas class="w-full h-full block"></canvas>`,
  styles: [`
    :host { display: block; width: 100%; height: 100%; }
  `]
})
export class PaddleSceneComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;

  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private animationFrameId = 0;

  private paddleGroup!: THREE.Group;
  private particles!: THREE.Points;

  private shots: Shot[] = [];
  private targetProgress = 0;
  private currentProgress = 0;

  private targetPointer = { x: 0, y: 0 };
  private currentPointer = { x: 0, y: 0 };

  ngAfterViewInit() {
    this.initScene();
    this.buildShots();
    this.animate();
  }

  ngOnDestroy() {
    cancelAnimationFrame(this.animationFrameId);
    if (!this.scene) return;
    this.scene.traverse((object: any) => {
      object.geometry?.dispose?.();
      if (Array.isArray(object.material)) {
        object.material.forEach((material: THREE.Material) => material.dispose());
      } else {
        object.material?.dispose?.();
      }
    });
    this.renderer?.dispose();
  }

  @HostListener('window:resize')
  onResize() {
    if (!this.renderer || !this.camera) return;
    const canvas = this.canvasRef.nativeElement;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    this.camera.aspect = width / Math.max(height, 1);
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
  }

  /** Llamado por el componente padre en cada onUpdate de ScrollTrigger. t en [0, 1]. */
  setProgress(t: number) {
    this.targetProgress = Math.min(1, Math.max(0, t));
  }

  /** Llamado por el componente padre en cada mousemove. nx/ny normalizados en [-1, 1]. */
  setPointer(nx: number, ny: number) {
    this.targetPointer.x = nx;
    this.targetPointer.y = ny;
  }

  private initScene() {
    const canvas = this.canvasRef.nativeElement;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x06060a);
    this.scene.fog = new THREE.Fog(0x06060a, 6, 16);

    this.camera = new THREE.PerspectiveCamera(38, width / Math.max(height, 1), 0.1, 100);
    this.camera.position.set(0, 0, 6);

    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
    this.renderer.setSize(width, height, false);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.15;

    this.addLights();
    this.paddleGroup = this.buildPaddle();
    this.scene.add(this.paddleGroup);
    this.particles = this.buildParticles();
    this.scene.add(this.particles);
  }

  private addLights() {
    this.scene.add(new THREE.HemisphereLight(0xeaf7ff, 0x0a0a10, 0.9));

    const key = new THREE.DirectionalLight(0xffffff, 2.4);
    key.position.set(-3, 4, 5);
    key.castShadow = true;
    key.shadow.mapSize.set(1024, 1024);
    this.scene.add(key);

    const rim = new THREE.PointLight(0xd9ff42, 6, 14, 2);
    rim.position.set(2.4, 1.2, -2.5);
    this.scene.add(rim);

    const fill = new THREE.PointLight(0x06b6d4, 3.2, 14, 2);
    fill.position.set(-2.6, -1.6, 2.8);
    this.scene.add(fill);
  }

  // --- Construcción procedural de la paleta de pádel ---

  private tracePaddleOutline(path: THREE.Path | THREE.Shape, scale: number) {
    const w = 0.62 * scale;
    const topY = 1.55 * scale;
    const midY = 0.75 * scale;
    const neckY = -0.15 * scale;
    const neckW = 0.16 * scale;

    path.moveTo(0, topY);
    path.bezierCurveTo(w * 1.05, topY, w * 1.35, midY * 1.15, w * 1.05, midY * 0.35);
    path.bezierCurveTo(w * 0.95, neckY + 0.35, neckW, neckY + 0.15, neckW, neckY);
    path.lineTo(-neckW, neckY);
    path.bezierCurveTo(-neckW, neckY + 0.15, -w * 0.95, neckY + 0.35, -w * 1.05, midY * 0.35);
    path.bezierCurveTo(-w * 1.35, midY * 1.15, -w * 1.05, topY, 0, topY);
  }

  private buildPaddle(): THREE.Group {
    const group = new THREE.Group();

    const headShape = new THREE.Shape();
    this.tracePaddleOutline(headShape, 1);
    const headGeo = new THREE.ExtrudeGeometry(headShape, {
      depth: 0.11,
      bevelEnabled: true,
      bevelThickness: 0.025,
      bevelSize: 0.02,
      bevelSegments: 3,
      curveSegments: 24
    });
    headGeo.center();
    headGeo.translate(0, 0.35, 0);
    const headMat = new THREE.MeshPhysicalMaterial({
      color: 0x14141a,
      map: this.createCarbonTexture(),
      roughness: 0.42,
      metalness: 0.15,
      clearcoat: 0.55,
      clearcoatRoughness: 0.25
    });
    const head = new THREE.Mesh(headGeo, headMat);
    head.castShadow = true;
    head.receiveShadow = true;
    group.add(head);

    const rimShape = new THREE.Shape();
    this.tracePaddleOutline(rimShape, 1.055);
    const hole = new THREE.Path();
    this.tracePaddleOutline(hole, 0.985);
    rimShape.holes.push(hole);
    const rimGeo = new THREE.ExtrudeGeometry(rimShape, { depth: 0.07, bevelEnabled: false, curveSegments: 24 });
    rimGeo.center();
    rimGeo.translate(0, 0.35, 0);
    const rimMat = new THREE.MeshStandardMaterial({
      color: 0xd9ff42,
      emissive: 0xa6e600,
      emissiveIntensity: 0.55,
      roughness: 0.3,
      metalness: 0.1
    });
    const rim = new THREE.Mesh(rimGeo, rimMat);
    rim.castShadow = true;
    group.add(rim);

    const handleMat = new THREE.MeshStandardMaterial({ color: 0x1c1c22, roughness: 0.5, metalness: 0.25 });
    const handle = new THREE.Mesh(new THREE.CylinderGeometry(0.075, 0.09, 0.85, 20), handleMat);
    handle.position.set(0, -0.95, 0);
    handle.castShadow = true;
    group.add(handle);

    const gripMat = new THREE.MeshStandardMaterial({
      color: 0x0d0d10,
      map: this.createGripTexture(),
      roughness: 0.75,
      metalness: 0.05
    });
    const grip = new THREE.Mesh(new THREE.CylinderGeometry(0.095, 0.11, 0.55, 20), gripMat);
    grip.position.set(0, -1.28, 0);
    grip.castShadow = true;
    group.add(grip);

    const capMat = new THREE.MeshStandardMaterial({ color: 0xd9ff42, emissive: 0xa6e600, emissiveIntensity: 0.4, roughness: 0.4 });
    const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.115, 0.115, 0.035, 20), capMat);
    cap.position.set(0, -1.555, 0);
    group.add(cap);

    group.rotation.z = 0.06;
    return group;
  }

  private createCarbonTexture(): THREE.CanvasTexture {
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 256;
    const ctx = canvas.getContext('2d')!;
    ctx.fillStyle = '#14141a';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.strokeStyle = 'rgba(255,255,255,0.05)';
    ctx.lineWidth = 1;
    const step = 10;
    for (let i = -canvas.height; i < canvas.width; i += step) {
      ctx.beginPath();
      ctx.moveTo(i, 0);
      ctx.lineTo(i + canvas.height, canvas.height);
      ctx.stroke();
      ctx.beginPath();
      ctx.moveTo(i + canvas.height, 0);
      ctx.lineTo(i, canvas.height);
      ctx.stroke();
    }
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  private createGripTexture(): THREE.CanvasTexture {
    const canvas = document.createElement('canvas');
    canvas.width = 128;
    canvas.height = 128;
    const ctx = canvas.getContext('2d')!;
    ctx.fillStyle = '#0d0d10';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.strokeStyle = 'rgba(217,255,66,0.35)';
    ctx.lineWidth = 6;
    for (let i = -canvas.width; i < canvas.width * 2; i += 22) {
      ctx.beginPath();
      ctx.moveTo(i, 0);
      ctx.lineTo(i + canvas.height, canvas.height);
      ctx.stroke();
    }
    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.repeat.set(2, 2);
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  private buildParticles(): THREE.Points {
    const count = 220;
    const positions = new Float32Array(count * 3);
    for (let i = 0; i < count; i++) {
      positions[i * 3] = (Math.random() - 0.5) * 14;
      positions[i * 3 + 1] = (Math.random() - 0.5) * 10;
      positions[i * 3 + 2] = (Math.random() - 0.5) * 10 - 2;
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    const material = new THREE.PointsMaterial({
      color: 0xd9ff42,
      size: 0.02,
      transparent: true,
      opacity: 0.5,
      depthWrite: false
    });
    return new THREE.Points(geometry, material);
  }

  // --- Shots de cámara sincronizados con el scroll ---

  private buildShots() {
    this.shots = [
      {
        t: 0,
        cameraPos: new THREE.Vector3(0, 0, 6),
        lookAt: new THREE.Vector3(0, 0.1, 0),
        paddlePos: new THREE.Vector3(0, 0, 0),
        paddleRot: new THREE.Euler(0.1, 0.5, 0.05)
      },
      {
        t: 0.33,
        cameraPos: new THREE.Vector3(2.2, 0.3, 4.4),
        lookAt: new THREE.Vector3(0, 0.2, 0),
        paddlePos: new THREE.Vector3(-0.3, 0, 0),
        paddleRot: new THREE.Euler(0.15, Math.PI * 0.85, 0.1)
      },
      {
        t: 0.66,
        cameraPos: new THREE.Vector3(-1.8, -0.4, 3.2),
        lookAt: new THREE.Vector3(0, 0.4, 0),
        paddlePos: new THREE.Vector3(0.2, 0.1, 0),
        paddleRot: new THREE.Euler(-0.1, Math.PI * 1.65, -0.05)
      },
      {
        t: 1,
        cameraPos: new THREE.Vector3(0, 0.1, 5.2),
        lookAt: new THREE.Vector3(0, 0.15, 0),
        paddlePos: new THREE.Vector3(0, 0, 0),
        paddleRot: new THREE.Euler(0.08, Math.PI * 2.4, 0.05)
      }
    ];
  }

  private evaluateShot(t: number): Shot {
    let i = 0;
    while (i < this.shots.length - 2 && t > this.shots[i + 1].t) i++;
    const a = this.shots[i];
    const b = this.shots[i + 1];
    const span = b.t - a.t || 1;
    const localT = Math.min(1, Math.max(0, (t - a.t) / span));
    const eased = localT * localT * (3 - 2 * localT); // smoothstep

    return {
      t,
      cameraPos: a.cameraPos.clone().lerp(b.cameraPos, eased),
      lookAt: a.lookAt.clone().lerp(b.lookAt, eased),
      paddlePos: a.paddlePos.clone().lerp(b.paddlePos, eased),
      paddleRot: new THREE.Euler(
        THREE.MathUtils.lerp(a.paddleRot.x, b.paddleRot.x, eased),
        THREE.MathUtils.lerp(a.paddleRot.y, b.paddleRot.y, eased),
        THREE.MathUtils.lerp(a.paddleRot.z, b.paddleRot.z, eased)
      )
    };
  }

  private animate() {
    this.animationFrameId = requestAnimationFrame(() => this.animate());

    // Suavizado (lerp) del scroll y del mouse para que el movimiento se sienta fluido,
    // no pegado 1:1 al evento crudo.
    this.currentProgress += (this.targetProgress - this.currentProgress) * 0.08;
    this.currentPointer.x += (this.targetPointer.x - this.currentPointer.x) * 0.06;
    this.currentPointer.y += (this.targetPointer.y - this.currentPointer.y) * 0.06;

    const shot = this.evaluateShot(this.currentProgress);

    this.camera.position.copy(shot.cameraPos);
    this.camera.position.x += this.currentPointer.x * 0.35;
    this.camera.position.y += this.currentPointer.y * 0.2;
    this.camera.lookAt(shot.lookAt);

    this.paddleGroup.position.copy(shot.paddlePos);
    this.paddleGroup.rotation.set(
      shot.paddleRot.x + this.currentPointer.y * 0.18,
      shot.paddleRot.y + this.currentPointer.x * 0.25,
      shot.paddleRot.z
    );

    this.particles.rotation.y += 0.0006;

    this.renderer.render(this.scene, this.camera);
  }
}
