import { AfterViewInit, Component, ElementRef, HostListener, OnDestroy, OnInit, input, output, signal, viewChild } from '@angular/core';

/** Lo que abre el editor: la cámara (webcam) o una foto ya elegida o sacada con la cámara del teléfono. */
export type FuenteFoto = { tipo: 'camara' } | { tipo: 'archivo'; archivo: File };

/** Lado del recuadro de recorte en pantalla (px CSS) y de la foto que se sube. */
const VISOR = 288;
const LADO_SALIDA = 512;
const ZOOM_MAXIMO = 4;
const BYTES_MAXIMOS_ARCHIVO = 25 * 1024 * 1024;

/**
 * Modal para elegir la foto de perfil: saca la foto con la webcam o recibe un archivo, y deja encuadrarla
 * (arrastrar y zoom) dentro de un círculo. Devuelve un JPEG cuadrado de 512px listo para subir: el backend
 * igual lo vuelve a procesar, pero así viaja liviano.
 */
@Component({
  selector: 'app-editor-foto',
  template: `
    <div class="fixed inset-0 z-[90] flex items-end sm:items-center justify-center bg-black/80 backdrop-blur-sm p-4" (click)="cancelar.emit()">
      <div role="dialog" aria-modal="true" aria-labelledby="titulo-editor-foto" (click)="$event.stopPropagation()"
           class="w-full max-w-sm rounded-3xl border border-white/10 bg-dark-950 p-5 shadow-2xl">
        <h3 id="titulo-editor-foto" class="font-display text-lg font-bold text-white">
          {{ etapa() === 'camara' ? 'Sacate una foto' : 'Encuadrá tu foto' }}
        </h3>
        <p class="text-xs text-slate-400 mt-0.5 mb-4">
          {{ etapa() === 'camara' ? 'Mirá a la cámara, con buena luz.' : 'Arrastrala y usá el zoom para que se vea bien tu cara.' }}
        </p>

        <div class="relative mx-auto overflow-hidden rounded-2xl bg-black touch-none select-none"
             [style.width.px]="visor" [style.height.px]="visor">
          @if (etapa() === 'camara') {
            <video #video autoplay playsinline muted class="w-full h-full object-cover -scale-x-100"></video>
            @if (!camaraLista() && !errorCamara()) {
              <div class="absolute inset-0 flex items-center justify-center text-xs text-slate-400">Abriendo la cámara…</div>
            }
            @if (errorCamara()) {
              <div class="absolute inset-0 flex items-center justify-center p-6 text-center text-sm text-amber-200">{{ errorCamara() }}</div>
            }
          } @else {
            <canvas #lienzo class="w-full h-full cursor-grab active:cursor-grabbing"
                    (pointerdown)="empezarArrastre($event)" (pointermove)="arrastrar($event)"
                    (pointerup)="terminarArrastre($event)" (pointercancel)="terminarArrastre($event)"
                    (wheel)="rueda($event)"></canvas>
          }
          <!-- Máscara redonda: así se va a ver el avatar -->
          <div class="pointer-events-none absolute inset-3 rounded-full ring-2 ring-white/70 shadow-[0_0_0_999px_rgba(0,0,0,0.55)]"></div>
        </div>

        @if (etapa() === 'recorte') {
          <label class="mt-4 flex items-center gap-3 text-xs text-slate-400">
            <span>Zoom</span>
            <input type="range" min="1" [max]="zoomMaximo" step="0.01" [value]="zoom()" (input)="cambiarZoom($any($event.target).valueAsNumber)"
                   class="flex-1 accent-primary-500" aria-label="Zoom de la foto" />
          </label>
        }

        <div class="mt-5 flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <button type="button" (click)="cancelar.emit()" class="btn-secondary py-2.5 px-4 text-sm">Cancelar</button>
          @if (etapa() === 'camara') {
            @if (errorCamara()) {
              <button type="button" (click)="pedirArchivo.emit()" class="btn-primary py-2.5 px-4 text-sm">Subir una foto</button>
            } @else {
              <button type="button" (click)="capturar()" [disabled]="!camaraLista()" class="btn-primary py-2.5 px-5 text-sm disabled:opacity-50">Sacar foto</button>
            }
          } @else {
            @if (desdeCamara()) {
              <button type="button" (click)="volverACamara()" class="py-2.5 px-4 rounded-xl border border-white/10 text-sm font-semibold text-slate-300 hover:bg-white/5 transition">Sacar otra</button>
            }
            <button type="button" (click)="confirmar()" [disabled]="!imagen() || exportando()" class="btn-primary py-2.5 px-5 text-sm disabled:opacity-50">
              {{ exportando() ? 'Preparando…' : 'Usar foto' }}
            </button>
          }
        </div>
      </div>
    </div>
  `
})
export class EditorFotoComponent implements OnInit, AfterViewInit, OnDestroy {
  fuente = input.required<FuenteFoto>();
  listo = output<Blob>();
  cancelar = output<void>();
  /** La cámara no está disponible: quien abrió el editor ofrece elegir un archivo. */
  pedirArchivo = output<void>();
  error = output<string>();

  readonly visor = VISOR;
  readonly zoomMaximo = ZOOM_MAXIMO;

  private video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private lienzo = viewChild<ElementRef<HTMLCanvasElement>>('lienzo');

  etapa = signal<'camara' | 'recorte'>('recorte');
  desdeCamara = signal(false);
  camaraLista = signal(false);
  errorCamara = signal<string | null>(null);
  imagen = signal<CanvasImageSource & { width: number; height: number } | null>(null);
  zoom = signal(1);
  exportando = signal(false);

  private stream: MediaStream | null = null;
  // Desplazamiento del centro de la imagen respecto del centro del visor, en px del visor.
  private dx = 0;
  private dy = 0;
  private arrastre: { id: number; x: number; y: number } | null = null;

  ngOnInit(): void {
    // La etapa se fija antes del primer render: así ya existe el <video> o el <canvas> que corresponde.
    const camara = this.fuente().tipo === 'camara';
    this.desdeCamara.set(camara);
    this.etapa.set(camara ? 'camara' : 'recorte');
  }

  ngAfterViewInit(): void {
    const fuente = this.fuente();
    if (fuente.tipo === 'camara') {
      this.abrirCamara();
    } else {
      this.cargarArchivo(fuente.archivo);
    }
  }

  ngOnDestroy(): void {
    this.cerrarCamara();
  }

  @HostListener('document:keydown.escape')
  alEscape(): void {
    this.cancelar.emit();
  }

  // ---------- Cámara ----------

  private async abrirCamara(): Promise<void> {
    this.etapa.set('camara');
    this.camaraLista.set(false);
    this.errorCamara.set(null);
    if (!navigator.mediaDevices?.getUserMedia) {
      this.errorCamara.set('Este navegador no permite usar la cámara. Podés subir una foto.');
      return;
    }
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 1280 }, height: { ideal: 1280 } },
        audio: false
      });
      // Al volver a sacar otra, el <video> se crea al pasar a la etapa "cámara": se espera a que esté.
      await new Promise(resolver => setTimeout(resolver));
      const video = this.video()?.nativeElement;
      if (!video) return;
      video.srcObject = this.stream;
      video.onloadedmetadata = () => this.camaraLista.set(true);
    } catch (e) {
      const nombre = (e as DOMException)?.name;
      this.errorCamara.set(nombre === 'NotAllowedError'
        ? 'No diste permiso para usar la cámara. Habilitalo en el navegador o subí una foto.'
        : nombre === 'NotFoundError'
          ? 'No encontramos una cámara en este dispositivo. Podés subir una foto.'
          : 'No pudimos abrir la cámara. Podés subir una foto.');
    }
  }

  private cerrarCamara(): void {
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = null;
    this.camaraLista.set(false);
  }

  capturar(): void {
    const video = this.video()?.nativeElement;
    if (!video || !video.videoWidth) return;
    const cuadro = document.createElement('canvas');
    cuadro.width = video.videoWidth;
    cuadro.height = video.videoHeight;
    const ctx = cuadro.getContext('2d')!;
    // La vista previa se ve como un espejo (como cualquier cámara frontal): se guarda igual que se vio.
    ctx.translate(cuadro.width, 0);
    ctx.scale(-1, 1);
    ctx.drawImage(video, 0, 0);
    this.cerrarCamara();
    this.mostrarParaRecortar(cuadro);
  }

  volverACamara(): void {
    this.imagen.set(null);
    this.abrirCamara();
  }

  // ---------- Archivo ----------

  private cargarArchivo(archivo: File): void {
    if (archivo.type && !archivo.type.startsWith('image/')) {
      this.error.emit('Ese archivo no es una imagen.');
      return;
    }
    if (archivo.size > BYTES_MAXIMOS_ARCHIVO) {
      this.error.emit('La foto es demasiado pesada (máximo 25 MB).');
      return;
    }
    const url = URL.createObjectURL(archivo);
    const img = new Image();
    // El navegador aplica la orientación EXIF al dibujar: las fotos del celular no quedan acostadas.
    img.onload = () => {
      URL.revokeObjectURL(url);
      this.mostrarParaRecortar(img);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      this.error.emit('No pudimos abrir esa imagen. Probá con una foto JPG o PNG.');
    };
    img.src = url;
  }

  // ---------- Recorte ----------

  private mostrarParaRecortar(fuente: HTMLImageElement | HTMLCanvasElement): void {
    const imagen = fuente instanceof HTMLImageElement
      ? Object.assign(fuente, { width: fuente.naturalWidth, height: fuente.naturalHeight })
      : fuente;
    this.imagen.set(imagen);
    this.zoom.set(1);
    this.dx = 0;
    this.dy = 0;
    this.etapa.set('recorte');
    // El canvas aparece al cambiar de etapa: se dibuja en el próximo cuadro.
    requestAnimationFrame(() => this.dibujar());
  }

  /** Escala base: la imagen cubre todo el visor sin dejar bordes. */
  private escala(): number {
    const img = this.imagen();
    if (!img) return 1;
    return Math.max(VISOR / img.width, VISOR / img.height) * this.zoom();
  }

  /** Que nunca quede un borde vacío dentro del recuadro. */
  private limitar(): void {
    const img = this.imagen();
    if (!img) return;
    const s = this.escala();
    const maxX = Math.max(0, (img.width * s - VISOR) / 2);
    const maxY = Math.max(0, (img.height * s - VISOR) / 2);
    this.dx = Math.min(maxX, Math.max(-maxX, this.dx));
    this.dy = Math.min(maxY, Math.max(-maxY, this.dy));
  }

  private pintar(ctx: CanvasRenderingContext2D, lado: number): void {
    const img = this.imagen();
    if (!img) return;
    const factor = lado / VISOR;
    const s = this.escala() * factor;
    const w = img.width * s;
    const h = img.height * s;
    ctx.imageSmoothingQuality = 'high';
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, lado, lado);
    ctx.drawImage(img, lado / 2 - w / 2 + this.dx * factor, lado / 2 - h / 2 + this.dy * factor, w, h);
  }

  private dibujar(): void {
    const lienzo = this.lienzo()?.nativeElement;
    if (!lienzo) return;
    const densidad = window.devicePixelRatio || 1;
    const lado = Math.round(VISOR * densidad);
    if (lienzo.width !== lado) {
      lienzo.width = lado;
      lienzo.height = lado;
    }
    this.pintar(lienzo.getContext('2d')!, lado);
  }

  cambiarZoom(valor: number): void {
    this.zoom.set(Math.min(ZOOM_MAXIMO, Math.max(1, valor || 1)));
    this.limitar();
    this.dibujar();
  }

  rueda(evento: WheelEvent): void {
    evento.preventDefault();
    this.cambiarZoom(this.zoom() * (evento.deltaY < 0 ? 1.08 : 1 / 1.08));
  }

  empezarArrastre(evento: PointerEvent): void {
    this.arrastre = { id: evento.pointerId, x: evento.clientX, y: evento.clientY };
    (evento.target as HTMLElement).setPointerCapture(evento.pointerId);
  }

  arrastrar(evento: PointerEvent): void {
    if (!this.arrastre || this.arrastre.id !== evento.pointerId) return;
    this.dx += evento.clientX - this.arrastre.x;
    this.dy += evento.clientY - this.arrastre.y;
    this.arrastre = { id: evento.pointerId, x: evento.clientX, y: evento.clientY };
    this.limitar();
    this.dibujar();
  }

  terminarArrastre(evento: PointerEvent): void {
    if (this.arrastre?.id === evento.pointerId) this.arrastre = null;
  }

  confirmar(): void {
    if (!this.imagen()) return;
    this.exportando.set(true);
    const salida = document.createElement('canvas');
    salida.width = LADO_SALIDA;
    salida.height = LADO_SALIDA;
    this.pintar(salida.getContext('2d')!, LADO_SALIDA);
    salida.toBlob(blob => {
      this.exportando.set(false);
      if (blob) {
        this.listo.emit(blob);
      } else {
        this.error.emit('No pudimos preparar la foto. Probá de nuevo.');
      }
    }, 'image/jpeg', 0.9);
  }
}
