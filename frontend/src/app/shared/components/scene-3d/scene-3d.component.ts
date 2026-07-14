import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, ViewChild } from '@angular/core';
import * as THREE from 'three';

@Component({
  selector: 'app-scene-3d',
  standalone: true,
  template: `<canvas #canvas class="w-full h-full block"></canvas>`,
  styles: [`
    :host {
      display: block;
      width: 100%;
      height: 100%;
    }
  `]
})
export class Scene3dComponent implements AfterViewInit, OnDestroy {
  @ViewChild('canvas') private canvasRef!: ElementRef<HTMLCanvasElement>;

  private renderer!: THREE.WebGLRenderer;
  private scene!: THREE.Scene;
  private camera!: THREE.PerspectiveCamera;
  private animationFrameId = 0;
  private ball!: THREE.Mesh;
  private ballShadow!: THREE.Mesh;
  private leftImpact!: THREE.Mesh;
  private rightImpact!: THREE.Mesh;

  ngAfterViewInit() {
    this.initScene();
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
    this.camera.aspect = width / height;
    this.setResponsiveCamera(width, height);
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
    this.renderScene();
  }

  private initScene() {
    const canvas = this.canvasRef.nativeElement;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x050507);
    this.scene.fog = new THREE.Fog(0x050507, 18, 44);

    this.camera = new THREE.PerspectiveCamera(40, width / height, 0.1, 100);
    this.setResponsiveCamera(width, height);

    this.renderer = new THREE.WebGLRenderer({
      canvas,
      antialias: true,
      powerPreference: 'high-performance'
    });
    this.renderer.setSize(width, height, false);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.shadowMap.enabled = true;
    this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.2;

    this.buildPadelCourt();
  }

  private buildPadelCourt() {
    const court = new THREE.Group();
    court.rotation.x = -0.02;
    this.scene.add(court);

    this.scene.add(new THREE.HemisphereLight(0xf4fff8, 0x08080c, 1.55));
    this.addAreaLight(-7, 8, 7, 0x52c41a, 3.2);
    this.addAreaLight(7, 8, -6, 0x06b6d4, 2.9);

    const keyLight = new THREE.DirectionalLight(0xffffff, 2.2);
    keyLight.position.set(-5, 10, 8);
    keyLight.castShadow = true;
    keyLight.shadow.mapSize.set(2048, 2048);
    keyLight.shadow.camera.left = -15;
    keyLight.shadow.camera.right = 15;
    keyLight.shadow.camera.top = 12;
    keyLight.shadow.camera.bottom = -12;
    this.scene.add(keyLight);

    const surface = new THREE.MeshStandardMaterial({
      color: 0x1f8b66,
      roughness: 0.82,
      metalness: 0.02,
      map: this.createTurfTexture(),
      bumpMap: this.createTurfTexture(),
      bumpScale: 0.025
    });
    const floor = new THREE.Mesh(new THREE.BoxGeometry(20, 0.08, 10), surface);
    floor.receiveShadow = true;
    court.add(floor);

    const surround = new THREE.Mesh(
      new THREE.PlaneGeometry(42, 30),
      new THREE.MeshStandardMaterial({ color: 0x09090d, roughness: 0.92, metalness: 0.05 })
    );
    surround.rotation.x = -Math.PI / 2;
    surround.position.y = -0.075;
    surround.receiveShadow = true;
    this.scene.add(surround);

    this.addLines(court);
    this.addNet(court);
    this.addGlass(court);
    this.addLightPosts(court);
    this.addBall();
  }

  private addLines(court: THREE.Group) {
    const mat = new THREE.MeshStandardMaterial({
      color: 0xf8fafc,
      roughness: 0.38,
      emissive: 0xffffff,
      emissiveIntensity: 0.08
    });
    this.box(court, 0, 0.035, -5, 20, 0.018, 0.075, mat);
    this.box(court, 0, 0.035, 5, 20, 0.018, 0.075, mat);
    this.box(court, -10, 0.035, 0, 0.075, 0.018, 10, mat);
    this.box(court, 10, 0.035, 0, 0.075, 0.018, 10, mat);
    this.box(court, -6.95, 0.04, 0, 0.07, 0.018, 10, mat);
    this.box(court, 6.95, 0.04, 0, 0.07, 0.018, 10, mat);
    this.box(court, 0, 0.04, 0, 13.9, 0.018, 0.065, mat);
  }

  private addNet(court: THREE.Group) {
    const netMat = new THREE.MeshStandardMaterial({
      color: 0x111827,
      transparent: true,
      opacity: 0.58,
      roughness: 0.7,
      side: THREE.DoubleSide
    });
    const net = new THREE.Mesh(new THREE.PlaneGeometry(10, 0.95, 18, 5), netMat);
    net.rotation.y = Math.PI / 2;
    net.position.set(0, 0.5, 0);
    court.add(net);

    const band = new THREE.MeshStandardMaterial({ color: 0xf8fafc, roughness: 0.4 });
    this.box(court, 0, 0.97, 0, 0.08, 0.08, 10.15, band);
    this.box(court, 0, 0.5, -5.08, 0.1, 1, 0.1, band);
    this.box(court, 0, 0.5, 5.08, 0.1, 1, 0.1, band);
  }

  private addGlass(court: THREE.Group) {
    const glass = new THREE.MeshPhysicalMaterial({
      color: 0xc9f7ff,
      transparent: true,
      opacity: 0.18,
      roughness: 0.04,
      metalness: 0,
      transmission: 0.42,
      thickness: 0.16,
      clearcoat: 0.8,
      clearcoatRoughness: 0.06,
      side: THREE.DoubleSide
    });
    const frame = new THREE.MeshStandardMaterial({ color: 0x12121a, metalness: 0.55, roughness: 0.32 });

    this.box(court, -10.06, 1.52, 0, 0.08, 3.04, 10.08, glass);
    this.box(court, 10.06, 1.52, 0, 0.08, 3.04, 10.08, glass);
    this.box(court, 0, 1.52, -5.06, 20.12, 3.04, 0.08, glass);
    this.box(court, 0, 1.52, 5.06, 20.12, 3.04, 0.08, glass);

    for (const x of [-10.12, 10.12]) {
      this.box(court, x, 3.07, 0, 0.07, 0.07, 10.16, frame);
      this.box(court, x, 0.06, 0, 0.07, 0.07, 10.16, frame);
    }
    for (const z of [-5.12, 5.12]) {
      this.box(court, 0, 3.07, z, 20.16, 0.07, 0.07, frame);
      this.box(court, 0, 0.06, z, 20.16, 0.07, 0.07, frame);
    }
    for (const x of [-10.12, -5.06, 0, 5.06, 10.12]) {
      this.box(court, x, 1.54, -5.12, 0.045, 3.02, 0.045, frame);
      this.box(court, x, 1.54, 5.12, 0.045, 3.02, 0.045, frame);
    }
    for (const z of [-5.12, -2.56, 0, 2.56, 5.12]) {
      this.box(court, -10.12, 1.54, z, 0.045, 3.02, 0.045, frame);
      this.box(court, 10.12, 1.54, z, 0.045, 3.02, 0.045, frame);
    }
  }

  private addBall() {
    const ballMat = new THREE.MeshStandardMaterial({
      color: 0xd9ff42,
      emissive: 0xb6ff2d,
      emissiveIntensity: 0.38,
      roughness: 0.42
    });
    this.ball = new THREE.Mesh(new THREE.SphereGeometry(0.18, 32, 32), ballMat);
    this.ball.castShadow = true;
    this.scene.add(this.ball);

    this.ballShadow = new THREE.Mesh(
      new THREE.CircleGeometry(0.34, 32),
      new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.3, depthWrite: false })
    );
    this.ballShadow.rotation.x = -Math.PI / 2;
    this.ballShadow.position.y = 0.045;
    this.scene.add(this.ballShadow);

    const impactMat = new THREE.MeshBasicMaterial({
      color: 0xd9ff42,
      transparent: true,
      opacity: 0,
      side: THREE.DoubleSide,
      depthWrite: false
    });
    this.leftImpact = new THREE.Mesh(new THREE.RingGeometry(0.18, 0.42, 36), impactMat.clone());
    this.rightImpact = new THREE.Mesh(new THREE.RingGeometry(0.18, 0.42, 36), impactMat.clone());
    this.leftImpact.rotation.y = Math.PI / 2;
    this.rightImpact.rotation.y = Math.PI / 2;
    this.leftImpact.position.set(-10.11, 1.25, 0);
    this.rightImpact.position.set(10.11, 1.25, 0);
    this.scene.add(this.leftImpact, this.rightImpact);
  }

  private addLightPosts(court: THREE.Group) {
    const metal = new THREE.MeshStandardMaterial({ color: 0x24242c, roughness: 0.36, metalness: 0.72 });
    for (const [x, z] of [[-9.5, -5.8], [9.5, 5.8], [-9.5, 5.8], [9.5, -5.8]]) {
      const mast = new THREE.Mesh(new THREE.CylinderGeometry(0.04, 0.055, 5.6, 12), metal);
      mast.position.set(x, 2.75, z);
      mast.castShadow = true;
      court.add(mast);
    }
  }

  private addAreaLight(x: number, y: number, z: number, color: number, intensity: number) {
    const light = new THREE.PointLight(color, intensity * 6, 22, 1.8);
    light.position.set(x, y, z);
    this.scene.add(light);
  }

  private box(group: THREE.Group, x: number, y: number, z: number, w: number, h: number, d: number, material: THREE.Material) {
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), material);
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
  }

  private createTurfTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 512;
    canvas.height = 512;
    const ctx = canvas.getContext('2d')!;
    ctx.fillStyle = '#1f8b66';
    ctx.fillRect(0, 0, canvas.width, canvas.height);

    for (let i = 0; i < 16000; i++) {
      const x = Math.random() * canvas.width;
      const y = Math.random() * canvas.height;
      const alpha = 0.06 + Math.random() * 0.14;
      ctx.strokeStyle = Math.random() > 0.48 ? `rgba(200,255,226,${alpha})` : `rgba(7,61,48,${alpha})`;
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.lineTo(x + (Math.random() - 0.5) * 5, y + 3 + Math.random() * 7);
      ctx.stroke();
    }

    const texture = new THREE.CanvasTexture(canvas);
    texture.wrapS = THREE.RepeatWrapping;
    texture.wrapT = THREE.RepeatWrapping;
    texture.repeat.set(12, 7);
    texture.anisotropy = this.renderer.capabilities.getMaxAnisotropy();
    texture.colorSpace = THREE.SRGBColorSpace;
    return texture;
  }

  private renderScene() {
    this.renderer.render(this.scene, this.camera);
  }

  private animate() {
    this.animationFrameId = requestAnimationFrame(() => this.animate());
    const time = performance.now() * 0.001;
    this.animateBall(time);
    this.renderScene();
  }

  private animateBall(time: number) {
    const cycle = 4.8;
    const t = (time % cycle) / cycle;
    const forward = t < 0.5;
    const half = forward ? t * 2 : (1 - t) * 2;
    const eased = this.easeInOutSine(half);
    const x = -9.58 + eased * 19.16;
    const z = Math.sin(time * 1.55) * 1.85;
    const bounce = Math.abs(Math.sin(t * Math.PI * 6));
    const y = 0.2 + Math.pow(bounce, 0.72) * 1.45;
    const wallHit = Math.max(
      0,
      1 - Math.min(Math.abs(x + 9.58), Math.abs(x - 9.58)) / 0.85
    );

    this.ball.position.set(x, y + wallHit * 0.1, z);
    this.ball.rotation.set(time * 6, time * 8, time * 3);
    this.ball.scale.setScalar(1 + wallHit * 0.18);

    this.ballShadow.position.set(x, 0.05, z);
    this.ballShadow.scale.setScalar(Math.max(0.45, 1.15 - y * 0.32));
    (this.ballShadow.material as THREE.MeshBasicMaterial).opacity = Math.max(0.08, 0.34 - y * 0.1);

    const leftOpacity = Math.max(0, 1 - Math.abs(x + 9.58) / 0.75);
    const rightOpacity = Math.max(0, 1 - Math.abs(x - 9.58) / 0.75);
    this.updateImpact(this.leftImpact, leftOpacity, z, y);
    this.updateImpact(this.rightImpact, rightOpacity, z, y);
  }

  private updateImpact(ring: THREE.Mesh, opacity: number, z: number, y: number) {
    ring.position.z = z;
    ring.position.y = Math.max(0.75, Math.min(2.4, y));
    ring.scale.setScalar(1 + opacity * 1.7);
    (ring.material as THREE.MeshBasicMaterial).opacity = opacity * 0.55;
  }

  private setResponsiveCamera(width: number, height: number) {
    const aspect = width / Math.max(height, 1);
    if (aspect < 0.85) {
      this.camera.fov = 50;
      this.camera.position.set(0, 11.5, 24.5);
    } else if (aspect < 1.35) {
      this.camera.fov = 44;
      this.camera.position.set(0, 9.8, 21);
    } else {
      this.camera.fov = 40;
      this.camera.position.set(0, 8.4, 18.5);
    }
    this.camera.lookAt(0, 0.45, 0);
  }

  private easeInOutSine(value: number) {
    return -(Math.cos(Math.PI * value) - 1) / 2;
  }
}
