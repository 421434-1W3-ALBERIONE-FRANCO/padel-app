import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, ViewChild } from '@angular/core';
import * as THREE from 'three';
import { RoomEnvironment } from 'three/examples/jsm/environments/RoomEnvironment.js';

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

  // --- Construcción procedural de la paleta de pádel ---

  // Perfil de pádel real (según foto de referencia): cabeza redondeada y
  // compacta (ancho casi igual al alto, no un óvalo elongado), con un
  // estrechamiento relativamente corto hacia el cuello.
  private readonly PADDLE_HALF_WIDTH = 0.66;

  private tracePaddleOutline(path: THREE.Path | THREE.Shape, scale: number) {
    const w = this.PADDLE_HALF_WIDTH * scale;
    const topY = 1.5 * scale;
    const midY = 0.85 * scale;
    const neckY = 0.05 * scale;
    const neckW = 0.13 * scale;

    path.moveTo(0, topY);
    path.bezierCurveTo(w * 1.05, topY, w * 1.15, midY * 1.08, w * 1.0, midY);
    path.bezierCurveTo(w * 0.85, midY * 0.5, w * 0.4, neckY + 0.16, neckW, neckY);
    path.lineTo(-neckW, neckY);
    path.bezierCurveTo(-w * 0.4, neckY + 0.16, -w * 0.85, midY * 0.5, -w * 1.0, midY);
    path.bezierCurveTo(-w * 1.15, midY * 1.08, -w * 1.05, topY, 0, topY);
  }

  private buildPaddle(): THREE.Group {
    const group = new THREE.Group();

    // Centro real de la cabeza (para el logo) y el listado de agujeros: se calculan
    // una sola vez y se reutilizan tanto para perforar la cabeza (geometría real)
    // como para recortar la calcomanía del logo en esos mismos puntos (ver más abajo).
    const logoCenter = new THREE.Vector2(0, 0.42);
    const holeCenters = this.computePerforationCenters();

    const headShape = new THREE.Shape();
    this.tracePaddleOutline(headShape, 1);
    this.addPerforations(headShape, holeCenters);
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
      roughness: 0.2,
      metalness: 0.2,
      clearcoat: 0.9,
      clearcoatRoughness: 0.08
    });
    const head = new THREE.Mesh(headGeo, headMat);
    head.castShadow = true;
    head.receiveShadow = true;
    group.add(head);

    // Placa interior sin perforar: sin esto, los agujeros dejarían ver el fondo de
    // la escena en vez del "núcleo" oscuro típico de una paleta real.
    const backingShape = new THREE.Shape();
    this.tracePaddleOutline(backingShape, 0.985);
    const backingGeo = new THREE.ExtrudeGeometry(backingShape, { depth: 0.05, bevelEnabled: false, curveSegments: 24 });
    backingGeo.center();
    backingGeo.translate(0, 0.35, 0);
    const backingMat = new THREE.MeshStandardMaterial({ color: 0x08080b, roughness: 0.95, metalness: 0 });
    const backing = new THREE.Mesh(backingGeo, backingMat);
    group.add(backing);

    const rimShape = new THREE.Shape();
    this.tracePaddleOutline(rimShape, 1.055);
    const hole = new THREE.Path();
    this.tracePaddleOutline(hole, 0.985);
    rimShape.holes.push(hole);
    const rimGeo = new THREE.ExtrudeGeometry(rimShape, { depth: 0.07, bevelEnabled: false, curveSegments: 24 });
    rimGeo.center();
    rimGeo.translate(0, 0.35, 0);
    const rimMat = new THREE.MeshPhysicalMaterial({
      color: 0x52c41a,
      emissive: 0x3fa112,
      emissiveIntensity: 0.35,
      roughness: 0.12,
      metalness: 0.1,
      clearcoat: 1,
      clearcoatRoughness: 0.06
    });
    const rim = new THREE.Mesh(rimGeo, rimMat);
    rim.castShadow = true;
    group.add(rim);

    // Ícono de la app directo sobre la superficie perforada (como una calcomanía
    // real de marca): tamaño proporcional al ancho de la paleta, no un valor fijo.
    // La textura de la calcomanía tiene los mismos agujeros recortados por
    // transparencia (destination-out) que la geometría de la cabeza, así el logo
    // se "pierde" a través de los agujeros reales en vez de taparlos por completo.
    // Unlit a propósito: tiene que leerse con su color real sin importar la luz.
    const logoSize = this.PADDLE_HALF_WIDTH * 1.5;
    const logoMat = new THREE.MeshBasicMaterial({
      map: this.createLogoTexture(logoCenter, logoSize, holeCenters),
      transparent: true,
      depthWrite: false
    });
    const logo = new THREE.Mesh(new THREE.PlaneGeometry(logoSize, logoSize), logoMat);
    logo.position.set(logoCenter.x, logoCenter.y, 0.09);
    group.add(logo);
    const logoBack = logo.clone();
    logoBack.position.z = -0.09;
    logoBack.rotation.y = Math.PI;
    group.add(logoBack);

    const handleMat = new THREE.MeshPhysicalMaterial({
      color: 0x1c1c22,
      roughness: 0.25,
      metalness: 0.3,
      clearcoat: 0.6,
      clearcoatRoughness: 0.15
    });
    const handle = new THREE.Mesh(new THREE.CylinderGeometry(0.075, 0.09, 0.85, 20), handleMat);
    handle.position.set(0, -0.8, 0);
    handle.castShadow = true;
    group.add(handle);

    const gripMat = new THREE.MeshStandardMaterial({
      color: 0x0d0d10,
      map: this.createGripTexture(),
      roughness: 0.75,
      metalness: 0.05
    });
    const grip = new THREE.Mesh(new THREE.CylinderGeometry(0.095, 0.11, 0.55, 20), gripMat);
    grip.position.set(0, -1.13, 0);
    grip.castShadow = true;
    group.add(grip);

    const capMat = new THREE.MeshPhysicalMaterial({
      color: 0x52c41a,
      emissive: 0x3fa112,
      emissiveIntensity: 0.3,
      roughness: 0.15,
      clearcoat: 1,
      clearcoatRoughness: 0.08
    });
    const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.115, 0.115, 0.035, 20), capMat);
    cap.position.set(0, -1.405, 0);
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
    ctx.strokeStyle = 'rgba(82,196,26,0.4)';
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

  /** Ancho aproximado del perfil de la paleta a una altura y dada (ver tracePaddleOutline),
   * usado para saber dónde entran perforaciones sin salirse del contorno real. */
  private maxHalfWidthAt(y: number): number {
    const w = this.PADDLE_HALF_WIDTH;
    const topY = 1.5;
    const midY = 0.85;
    const neckY = 0.05;

    if (y >= topY) return 0;
    if (y >= midY) {
      const t = (topY - y) / (topY - midY);
      return w * Math.sin((t * Math.PI) / 2);
    }
    if (y >= neckY) {
      const t = (midY - y) / (midY - neckY);
      return THREE.MathUtils.lerp(w, 0.13, t);
    }
    return 0;
  }

  private readonly HOLE_RADIUS = 0.052;

  /** Calcula el centro de cada agujero de la grilla, en el mismo espacio 2D del
   * contorno de la paleta. Se computa una única vez y se reutiliza tanto para
   * perforar la geometría real de la cabeza como para recortar la calcomanía
   * del logo exactamente en esos mismos puntos (ver createLogoTexture). */
  private computePerforationCenters(): THREE.Vector2[] {
    const holeRadius = this.HOLE_RADIUS;
    const marginFromEdge = 0.15;
    const spacing = holeRadius * 2.3;
    const rows = 12;
    const yStart = -0.02;
    const yEnd = 1.42;
    const centers: THREE.Vector2[] = [];

    for (let r = 0; r < rows; r++) {
      const y = THREE.MathUtils.lerp(yStart, yEnd, r / (rows - 1));
      const maxW = this.maxHalfWidthAt(y) - marginFromEdge;
      if (maxW <= holeRadius) continue;

      const usableW = maxW - holeRadius;
      const colsAtRow = Math.max(1, Math.floor((usableW * 2) / spacing) + 1);

      for (let c = 0; c < colsAtRow; c++) {
        const x = colsAtRow === 1 ? 0 : THREE.MathUtils.lerp(-usableW, usableW, c / (colsAtRow - 1));
        centers.push(new THREE.Vector2(x, y));
      }
    }
    return centers;
  }

  /** Perfora TODA la cara de la paleta con la grilla de agujeros redondos
   * característica del pádel, en los centros ya calculados por computePerforationCenters. */
  private addPerforations(shape: THREE.Shape, centers: THREE.Vector2[]) {
    for (const center of centers) {
      const hole = new THREE.Path();
      hole.absarc(center.x, center.y, this.HOLE_RADIUS, 0, Math.PI * 2, true);
      shape.holes.push(hole);
    }
  }

  /** Ícono de la app (mismo heroicon y degradé primary->turquoise del login) apoyado
   * sobre la superficie perforada como una calcomanía real. Para que se sienta
   * integrado a la paleta (y no flotando encima), se le recortan agujeros
   * transparentes exactamente donde caen los agujeros físicos de la cabeza: así
   * la calcomanía se "pierde" a través de las perforaciones reales en vez de
   * taparlas por completo. */
  private createLogoTexture(logoCenter: THREE.Vector2, logoSize: number, holeCenters: THREE.Vector2[]): THREE.CanvasTexture {
    const size = 512;
    const canvas = document.createElement('canvas');
    canvas.width = size;
    canvas.height = size;
    const ctx = canvas.getContext('2d')!;

    ctx.save();
    const iconScale = (size / 24) * 0.62;
    ctx.translate(size / 2, size / 2);
    ctx.scale(iconScale, iconScale);
    ctx.translate(-12, -12);

    // El heroicon original es un dibujo de línea (fill="none", stroke), no una forma
    // rellenable: hay que trazarlo igual que el SVG del login, si no el fill da una
    // mancha casi invisible.
    const gradient = ctx.createLinearGradient(2, 2, 22, 22);
    gradient.addColorStop(0, '#52c41a');
    gradient.addColorStop(1, '#06b6d4');
    const p1 = new Path2D('M15.362 5.214A8.252 8.252 0 0 1 12 21 8.25 8.25 0 0 1 6.038 7.047 8.287 8.287 0 0 0 9 9.601a8.983 8.983 0 0 1 3.361-6.867 8.21 8.21 0 0 0 3 2.48Z');
    const p2 = new Path2D('M12 18a3.75 3.75 0 0 0 .495-7.467 5.99 5.99 0 0 0-1.925 3.546 5.974 5.974 0 0 1-2.133-1A3.75 3.75 0 0 0 12 18Z');
    ctx.strokeStyle = gradient;
    ctx.lineWidth = 2.6;
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
    ctx.stroke(p1);
    ctx.stroke(p2);
    ctx.restore();

    // Recorte de agujeros: mismo centro y radio físico que la perforación real,
    // convertidos del espacio 2D de la paleta al espacio de píxeles de esta textura.
    const pxPerUnit = size / logoSize;
    const holeRadiusPx = this.HOLE_RADIUS * pxPerUnit * 1.05;
    ctx.globalCompositeOperation = 'destination-out';
    for (const hole of holeCenters) {
      const px = size / 2 + (hole.x - logoCenter.x) * pxPerUnit;
      const py = size / 2 - (hole.y - logoCenter.y) * pxPerUnit;
      if (px < -holeRadiusPx || px > size + holeRadiusPx || py < -holeRadiusPx || py > size + holeRadiusPx) continue;
      ctx.beginPath();
      ctx.arc(px, py, holeRadiusPx, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.globalCompositeOperation = 'source-over';

    const texture = new THREE.CanvasTexture(canvas);
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
