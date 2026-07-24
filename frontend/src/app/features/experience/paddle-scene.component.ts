import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, ViewChild } from '@angular/core';
import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';

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
  private balls: THREE.Mesh[] = [];
  private ballData: { basePos: THREE.Vector3; depth: number; phase: number; speed: number }[] = [];

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

    // Environment map procedural (sin HDRI externo) para que el plástico/carbono
    // glossy tenga reflejos reales en vez de solo especular de luces puntuales.
    const pmrem = new THREE.PMREMGenerator(this.renderer);
    this.scene.environment = pmrem.fromScene(new RoomEnvironment(), 0.04).texture;
    pmrem.dispose();

    this.addLights();
    this.paddleGroup = this.buildPaddle();
    this.scene.add(this.paddleGroup);
    this.particles = this.buildParticles();
    this.scene.add(this.particles);
    this.buildBalls();
  }

  /** Pelotas de pádel flotando alrededor de la paleta: cada una tiene su propia
   * profundidad (parallax al mouse), fase de rebote y drift con el scroll. */
  private buildBalls() {
    // Textura con costura curva + fieltro moteado: una esfera lisa con un aro
    // recto no se lee como pelota de pádel/tenis real, la curva envolvente sí.
    const ballMat = new THREE.MeshPhysicalMaterial({
      map: this.createBallTexture(),
      roughness: 0.6,
      clearcoat: 0.25,
      clearcoatRoughness: 0.45
    });

    const configs = [
      { x: -2.6, y: 1.3, z: -1.6, r: 0.22, depth: 0.55 },
      { x: 2.9, y: -0.9, z: -2.3, r: 0.28, depth: 0.9 },
      { x: -3.3, y: -1.5, z: -0.7, r: 0.17, depth: 0.35 },
      { x: 3.5, y: 1.7, z: -1.1, r: 0.2, depth: 0.5 },
      { x: 0.3, y: -2.3, z: -2.7, r: 0.25, depth: 1.05 }
    ];

    for (const cfg of configs) {
      const ball = new THREE.Mesh(new THREE.SphereGeometry(cfg.r, 32, 32), ballMat);
      ball.position.set(cfg.x, cfg.y, cfg.z);
      ball.rotation.set(Math.random() * Math.PI, Math.random() * Math.PI, Math.random() * Math.PI);
      ball.castShadow = true;
      this.scene.add(ball);

      this.balls.push(ball);
      this.ballData.push({
        basePos: ball.position.clone(),
        depth: cfg.depth,
        phase: Math.random() * Math.PI * 2,
        speed: 0.45 + Math.random() * 0.4
      });
    }
  }

  /** Textura equirectangular: base afelpada moteada + la costura curva envolvente
   * característica de una pelota de tenis/pádel (una onda que, al envolver la
   * esfera, dibuja la clásica "S"). */
  private createBallTexture(): THREE.CanvasTexture {
    const w = 512;
    const h = 256;
    const canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext('2d')!;

    const bg = ctx.createLinearGradient(0, 0, 0, h);
    bg.addColorStop(0, '#8fe040');
    bg.addColorStop(0.5, '#52c41a');
    bg.addColorStop(1, '#2c6d0e');
    ctx.fillStyle = bg;
    ctx.fillRect(0, 0, w, h);

    for (let i = 0; i < 3000; i++) {
      const x = Math.random() * w;
      const y = Math.random() * h;
      ctx.fillStyle = Math.random() > 0.5
        ? `rgba(255,255,255,${0.03 + Math.random() * 0.07})`
        : `rgba(0,35,0,${0.03 + Math.random() * 0.07})`;
      ctx.fillRect(x, y, 1.3, 1.3);
    }

    const seamPath = (offsetY: number) => {
      ctx.beginPath();
      for (let x = 0; x <= w; x++) {
        const u = x / w;
        const y = h / 2 + Math.sin(u * Math.PI * 2) * (h * 0.24) + offsetY;
        if (x === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.stroke();
    };
    ctx.strokeStyle = 'rgba(0,25,0,0.3)';
    ctx.lineWidth = h * 0.014;
    ctx.lineCap = 'round';
    seamPath(h * 0.018);
    ctx.strokeStyle = 'rgba(255,255,255,0.88)';
    ctx.lineWidth = h * 0.03;
    seamPath(0);

    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.RepeatWrapping;
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  private addLights() {
    // Iluminación de estudio: más ambiente parejo (hemisphere fuerte) y menos
    // dependencia de un solo key light duro, ahora que el environment map ya
    // aporta reflejos suaves de por sí.
    this.scene.add(new THREE.HemisphereLight(0xf5f8ff, 0x0c0c12, 1.3));

    const key = new THREE.DirectionalLight(0xffffff, 1.7);
    key.position.set(-3, 4, 5);
    key.castShadow = true;
    key.shadow.mapSize.set(1024, 1024);
    this.scene.add(key);

    const soft = new THREE.DirectionalLight(0xffffff, 0.6);
    soft.position.set(3, 2, -4);
    this.scene.add(soft);

    const rim = new THREE.PointLight(0x52c41a, 4, 14, 2);
    rim.position.set(2.4, 1.2, -2.5);
    this.scene.add(rim);

    const fill = new THREE.PointLight(0x06b6d4, 2.2, 14, 2);
    fill.position.set(-2.6, -1.6, 2.8);
    this.scene.add(fill);
  }

  // --- Modelo real de la paleta (GLB) ---

  private readonly PADDLE_MODEL_URL = '/models/paletaPLLD.glb';
  private readonly PADDLE_TARGET_HEIGHT = 2.6;

  private buildPaddle(): THREE.Group {
    const group = new THREE.Group();
    this.loadPaddleModel(group);
    return group;
  }

  /** Carga asincrónica del modelo 3D real de la paleta (reemplaza la construcción
   * procedural anterior) y lo normaliza a la escala/pivote que ya esperan los
   * shots de cámara: altura total fija y pivote centrado en el objeto, igual que
   * antes, para que el resto de la coreografía de cámara/scroll no se vea afectada. */
  private loadPaddleModel(group: THREE.Group) {
    const loader = new GLTFLoader();
    loader.load(
      this.PADDLE_MODEL_URL,
      (gltf) => {
        const root = gltf.scene;

        // El export (Blender/Tripo) trae además un nodo "Cube" placeholder gris de
        // referencia que no es parte de la paleta: se quita del árbol (no solo se
        // oculta) antes de medir el bounding box, para que no infle la escala ni
        // se vea flotando junto al racket real.
        root.getObjectByName('Cube')?.removeFromParent();

        const box = new THREE.Box3().setFromObject(root);
        const size = box.getSize(new THREE.Vector3());
        const center = box.getCenter(new THREE.Vector3());
        const scale = this.PADDLE_TARGET_HEIGHT / Math.max(size.y, 0.0001);

        root.scale.setScalar(scale);
        root.position.set(-center.x * scale, -center.y * scale, -center.z * scale);

        root.traverse((child) => {
          const mesh = child as THREE.Mesh;
          if (mesh.isMesh) {
            mesh.castShadow = true;
            mesh.receiveShadow = true;
          }
        });

        group.add(root);
      },
      undefined,
      (error) => console.error('No se pudo cargar el modelo 3D de la paleta', error)
    );
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
      color: 0x52c41a,
      size: 0.02,
      transparent: true,
      opacity: 0.5,
      depthWrite: false
    });
    return new THREE.Points(geometry, material);
  }

  // --- Shots de cámara sincronizados con el scroll ---

  private buildShots() {
    // 5 shots para las 5 secciones de /experience: hero, dos ángulos de detalle,
    // un plano general para la sección de catálogo, y un frente limpio para el CTA.
    //
    // Giro en Y pensado como una sola vuelta continua: arranca "cruzada" (0.5 rad,
    // ~29°) y termina en 2π, que es el mismo ángulo visual que 0 (totalmente recta
    // de frente a cámara) pero sin que el giro retroceda de golpe en el último tramo.
    // El total recorrido se recortó ~18% respecto de la versión anterior para que
    // se sienta más pausado sin cambiar el guion de cámara de cada sección.
    this.shots = [
      {
        t: 0,
        cameraPos: new THREE.Vector3(0, 0, 6),
        lookAt: new THREE.Vector3(0, 0.1, 0),
        paddlePos: new THREE.Vector3(0, 0, 0),
        paddleRot: new THREE.Euler(0.1, 0.5, 0.05)
      },
      {
        t: 0.25,
        cameraPos: new THREE.Vector3(2.2, 0.3, 4.4),
        lookAt: new THREE.Vector3(0, 0.2, 0),
        paddlePos: new THREE.Vector3(-0.3, 0, 0),
        paddleRot: new THREE.Euler(0.15, 2.28, 0.1)
      },
      {
        t: 0.5,
        cameraPos: new THREE.Vector3(-1.8, -0.4, 3.2),
        lookAt: new THREE.Vector3(0, 0.4, 0),
        paddlePos: new THREE.Vector3(0.2, 0.1, 0),
        paddleRot: new THREE.Euler(-0.1, 4.35, -0.05)
      },
      {
        t: 0.75,
        cameraPos: new THREE.Vector3(1.4, 0.6, 7.2),
        lookAt: new THREE.Vector3(0, 0.1, 0),
        paddlePos: new THREE.Vector3(0.6, -0.2, 0),
        paddleRot: new THREE.Euler(0.04, 5.38, 0.03)
      },
      {
        t: 1,
        cameraPos: new THREE.Vector3(0, 0.1, 5.2),
        lookAt: new THREE.Vector3(0, 0.15, 0),
        paddlePos: new THREE.Vector3(0, 0, 0),
        paddleRot: new THREE.Euler(0, Math.PI * 2, 0)
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
    this.animateBalls(performance.now() * 0.001);

    this.renderer.render(this.scene, this.camera);
  }

  private animateBalls(time: number) {
    for (let i = 0; i < this.balls.length; i++) {
      const ball = this.balls[i];
      const d = this.ballData[i];
      const bob = Math.sin(time * d.speed + d.phase) * 0.15;
      const drift = Math.sin(this.currentProgress * Math.PI * 2 + d.phase) * d.depth * 0.4;

      ball.position.x = d.basePos.x + this.currentPointer.x * d.depth + drift;
      ball.position.y = d.basePos.y + bob + this.currentPointer.y * d.depth * 0.6;
      ball.rotation.x += 0.01;
      ball.rotation.y += 0.014;
    }
  }
}
