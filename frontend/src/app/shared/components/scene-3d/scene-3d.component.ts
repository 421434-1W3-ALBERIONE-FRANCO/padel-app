import { Component, ElementRef, ViewChild, AfterViewInit, OnDestroy, HostListener } from '@angular/core';
import * as THREE from 'three';

@Component({
  selector: 'app-scene-3d',
  standalone: true,
  template: `
    <canvas #canvas class="w-full h-full block"></canvas>
  `,
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
  private animationFrameId!: number;
  private courtGroup!: THREE.Group;
  private ballMesh!: THREE.Mesh;

  // For parallax tilt
  private mouseX = 0;
  private mouseY = 0;
  private targetX = 0;
  private targetY = 0;

  ngAfterViewInit() {
    try {
      this.initThree();
      this.createCourt();
      this.animate();
    } catch (error) {
      console.warn('WebGL or Three.js failed to initialize, using CSS fallback:', error);
    }
  }

  ngOnDestroy() {
    this.cleanup();
  }

  @HostListener('document:mousemove', ['$event'])
  onMouseMove(event: MouseEvent) {
    // Normalize mouse coords between -1 and 1
    this.mouseX = (event.clientX / window.innerWidth) * 2 - 1;
    this.mouseY = -(event.clientY / window.innerHeight) * 2 + 1;
  }

  @HostListener('window:resize')
  onResize() {
    if (!this.canvasRef || !this.camera || !this.renderer) return;
    const width = this.canvasRef.nativeElement.clientWidth;
    const height = this.canvasRef.nativeElement.clientHeight;
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(width, height, false);
  }

  private initThree() {
    const canvas = this.canvasRef.nativeElement;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;

    this.scene = new THREE.Scene();
    // Soft fog for depth
    this.scene.fog = new THREE.FogExp2(0x0b0f19, 0.015);

    this.camera = new THREE.PerspectiveCamera(45, width / height, 0.1, 100);
    // Position camera looking down at the court from an angle
    this.camera.position.set(0, 11, 21);
    this.camera.lookAt(0, 0, 0);

    this.renderer = new THREE.WebGLRenderer({
      canvas: canvas,
      antialias: true,
      alpha: true,
      powerPreference: 'high-performance'
    });
    this.renderer.setSize(width, height, false);
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  }

  private createCourt() {
    this.courtGroup = new THREE.Group();

    // Read brand colors
    const style = getComputedStyle(document.documentElement);
    const primaryHex = style.getPropertyValue('--color-primary-500').trim() || '#6eb713';
    const accentHex = style.getPropertyValue('--color-accent-500').trim() || '#10b981';

    const courtColor = new THREE.Color(primaryHex);
    const accentColor = new THREE.Color(accentHex);
    const lineMaterial = new THREE.LineBasicMaterial({ color: courtColor, linewidth: 2 });
    const netMaterial = new THREE.LineBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.35 });
    const wallMaterial = new THREE.LineBasicMaterial({ color: accentColor, transparent: true, opacity: 0.18 });

    // 1. Court boundaries (20m x 10m)
    const courtGeo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-10, 0, -5),
      new THREE.Vector3(10, 0, -5),
      new THREE.Vector3(10, 0, 5),
      new THREE.Vector3(-10, 0, 5),
      new THREE.Vector3(-10, 0, -5)
    ]);
    const outerLines = new THREE.Line(courtGeo, lineMaterial);
    this.courtGroup.add(outerLines);

    // 2. Service lines (6.95m from the net on both sides)
    const serviceGeo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-6.95, 0, -5),
      new THREE.Vector3(-6.95, 0, 5),
      new THREE.Vector3(-6.95, 0, 0), // center joint
      new THREE.Vector3(6.95, 0, 0),
      new THREE.Vector3(6.95, 0, 5),
      new THREE.Vector3(6.95, 0, -5)
    ]);
    const serviceLines = new THREE.Line(serviceGeo, lineMaterial);
    this.courtGroup.add(serviceLines);

    // 3. Center service line (between the two service lines)
    const centerServiceGeo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-6.95, 0, 0),
      new THREE.Vector3(6.95, 0, 0)
    ]);
    const centerServiceLine = new THREE.Line(centerServiceGeo, lineMaterial);
    this.courtGroup.add(centerServiceLine);

    // 4. Net (center x = 0, height = 0.92m, width = 10m)
    const netPoints: THREE.Vector3[] = [];
    netPoints.push(new THREE.Vector3(0, 0, -5));
    netPoints.push(new THREE.Vector3(0, 1.0, -5)); // Pole
    netPoints.push(new THREE.Vector3(0, 0.92, -5));
    netPoints.push(new THREE.Vector3(0, 0.92, 5)); // Net top
    netPoints.push(new THREE.Vector3(0, 1.0, 5)); // Pole
    netPoints.push(new THREE.Vector3(0, 0, 5));

    // Simple grid pattern for the net
    for (let z = -5; z <= 5; z += 0.5) {
      netPoints.push(new THREE.Vector3(0, 0, z));
      netPoints.push(new THREE.Vector3(0, 0.92, z));
    }
    const netGeo = new THREE.BufferGeometry().setFromPoints(netPoints);
    const netLines = new THREE.Line(netGeo, netMaterial);
    this.courtGroup.add(netLines);

    // 5. End glass walls (3m high)
    const endWallLeftGeo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-10, 0, -5),
      new THREE.Vector3(-10, 3, -5),
      new THREE.Vector3(-10, 3, 5),
      new THREE.Vector3(-10, 0, 5),
      new THREE.Vector3(-10, 0, -5),
      new THREE.Vector3(-10, 3, -2),
      new THREE.Vector3(-10, 0, -2),
      new THREE.Vector3(-10, 0, 2),
      new THREE.Vector3(-10, 3, 2)
    ]);
    const endWallLeft = new THREE.Line(endWallLeftGeo, wallMaterial);
    this.courtGroup.add(endWallLeft);

    const endWallRightGeo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(10, 0, -5),
      new THREE.Vector3(10, 3, -5),
      new THREE.Vector3(10, 3, 5),
      new THREE.Vector3(10, 0, 5),
      new THREE.Vector3(10, 0, -5),
      new THREE.Vector3(10, 3, -2),
      new THREE.Vector3(10, 0, -2),
      new THREE.Vector3(10, 0, 2),
      new THREE.Vector3(10, 3, 2)
    ]);
    const endWallRight = new THREE.Line(endWallRightGeo, wallMaterial);
    this.courtGroup.add(endWallRight);

    // 6. Side glass/mesh return corners
    const cornersGeo = new THREE.BufferGeometry().setFromPoints([
      new THREE.Vector3(-10, 0, -5), new THREE.Vector3(-8, 0, -5),
      new THREE.Vector3(-8, 3, -5), new THREE.Vector3(-10, 3, -5),
      new THREE.Vector3(-10, 0, 5), new THREE.Vector3(-8, 0, 5),
      new THREE.Vector3(-8, 3, 5), new THREE.Vector3(-10, 3, 5),
      new THREE.Vector3(10, 0, -5), new THREE.Vector3(8, 0, -5),
      new THREE.Vector3(8, 3, -5), new THREE.Vector3(10, 3, -5),
      new THREE.Vector3(10, 0, 5), new THREE.Vector3(8, 0, 5),
      new THREE.Vector3(8, 3, 5), new THREE.Vector3(10, 3, 5)
    ]);
    const corners = new THREE.LineSegments(cornersGeo, wallMaterial);
    this.courtGroup.add(corners);

    this.scene.add(this.courtGroup);

    // 7. Bouncing Tennis/Padel Ball
    const ballColor = new THREE.Color(accentHex);
    const ballGeo = new THREE.SphereGeometry(0.18, 16, 16);
    const ballMat = new THREE.MeshBasicMaterial({ color: ballColor });
    this.ballMesh = new THREE.Mesh(ballGeo, ballMat);
    this.scene.add(this.ballMesh);

    // Shadows/Floor grid helper
    const gridHelper = new THREE.GridHelper(30, 30, 0x1e293b, 0x111827);
    gridHelper.position.y = -0.01;
    this.scene.add(gridHelper);
  }

  private animate() {
    this.animationFrameId = requestAnimationFrame(() => this.animate());

    const time = Date.now() * 0.002;

    // Rally Physics Simulation:
    // x travels cross court back and forth
    this.ballMesh.position.x = Math.sin(time * 0.7) * 7.5;
    // y bounces smoothly (cos amplitude, hitting the ground twice per full cycle)
    this.ballMesh.position.y = Math.abs(Math.cos(time * 1.4)) * 2.2 + 0.18;
    // z slides diagonally slightly
    this.ballMesh.position.z = Math.sin(time * 0.35) * 2.5;

    // Smooth tilt effect according to target mouse coordinates
    this.targetX = this.mouseX * 0.05;
    this.targetY = this.mouseY * 0.05;

    if (this.courtGroup) {
      this.courtGroup.rotation.y += (this.targetX - this.courtGroup.rotation.y) * 0.05;
      this.courtGroup.rotation.x += (this.targetY - this.courtGroup.rotation.x) * 0.05;
    }

    if (this.renderer && this.scene && this.camera) {
      this.renderer.render(this.scene, this.camera);
    }
  }

  private cleanup() {
    cancelAnimationFrame(this.animationFrameId);

    if (this.renderer) {
      this.renderer.dispose();
    }

    if (this.scene) {
      this.scene.traverse((object: any) => {
        if (object.geometry) {
          object.geometry.dispose();
        }
        if (object.material) {
          if (Array.isArray(object.material)) {
            object.material.forEach((material: any) => material.dispose());
          } else {
            object.material.dispose();
          }
        }
      });
    }
  }
}
