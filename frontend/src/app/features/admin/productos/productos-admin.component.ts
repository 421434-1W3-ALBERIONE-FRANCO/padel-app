import { Component, OnInit, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ProductoService } from '../../../core/services/producto.service';
import { ProductoResponse } from '../../../shared/models/producto.model';

@Component({
  selector: 'app-productos-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink],
  templateUrl: './productos-admin.component.html',
  styleUrls: ['./productos-admin.component.css']
})
export class ProductosAdminComponent implements OnInit {
  private productoService = inject(ProductoService);
  private fb = inject(FormBuilder);

  productos = signal<ProductoResponse[]>([]);
  cargando = signal<boolean>(false);
  mensajeError = signal<string | null>(null);
  mensajeExito = signal<string | null>(null);

  productoForm!: FormGroup;
  showForm = signal<boolean>(false);
  editProductoId = signal<number | null>(null);

  categorias = ['BEBIDA', 'SNACK', 'ACCESORIO', 'ALQUILER', 'OTROS'];

  ngOnInit() {
    this.initForm();
    this.cargarProductos();
  }

  initForm() {
    this.productoForm = this.fb.group({
      nombre: ['', [Validators.required, Validators.maxLength(100)]],
      categoria: ['BEBIDA', [Validators.required, Validators.maxLength(50)]],
      precio: [0, [Validators.required, Validators.min(0.01)]],
      stock: [0, [Validators.required, Validators.min(0)]],
      activo: [true]
    });
  }

  cargarProductos() {
    this.cargando.set(true);
    this.mensajeError.set(null);
    this.productoService.listarActivos().subscribe({
      next: (data) => {
        this.productos.set(data);
        this.cargando.set(false);
      },
      error: (err) => {
        this.mensajeError.set(err.error?.message || 'Error al cargar el catálogo de productos.');
        this.cargando.set(false);
      }
    });
  }

  abrirNuevo() {
    this.editProductoId.set(null);
    this.productoForm.reset({
      nombre: '',
      categoria: 'BEBIDA',
      precio: 0,
      stock: 0,
      activo: true
    });
    this.showForm.set(true);
    this.mensajeError.set(null);
    this.mensajeExito.set(null);
  }

  editar(producto: ProductoResponse) {
    this.editProductoId.set(producto.id);
    this.productoForm.patchValue({
      nombre: producto.nombre,
      categoria: producto.categoria,
      precio: producto.precio,
      stock: producto.stock,
      activo: producto.activo
    });
    this.showForm.set(true);
    this.mensajeError.set(null);
    this.mensajeExito.set(null);
  }

  cerrarForm() {
    this.showForm.set(false);
    this.editProductoId.set(null);
  }

  guardar() {
    if (this.productoForm.invalid) {
      this.productoForm.markAllAsTouched();
      return;
    }

    const payload = this.productoForm.value;
    const request$ = this.editProductoId()
      ? this.productoService.actualizar(this.editProductoId()!, payload)
      : this.productoService.crear(payload);

    this.cargando.set(true);
    this.mensajeError.set(null);
    this.mensajeExito.set(null);

    request$.subscribe({
      next: () => {
        this.mensajeExito.set(this.editProductoId() ? 'Producto actualizado con éxito' : 'Producto creado con éxito');
        this.cerrarForm();
        this.cargarProductos();
      },
      error: (err) => {
        this.mensajeError.set(err.error?.message || 'Error al guardar el producto.');
        this.cargando.set(false);
      }
    });
  }

  eliminar(id: number) {
    if (!confirm('¿Estás seguro de que deseas eliminar este producto?')) return;

    this.cargando.set(true);
    this.mensajeError.set(null);
    this.mensajeExito.set(null);

    this.productoService.eliminar(id).subscribe({
      next: () => {
        this.mensajeExito.set('Producto eliminado con éxito');
        this.cargarProductos();
      },
      error: (err) => {
        this.mensajeError.set(err.error?.message || 'Error al eliminar el producto.');
        this.cargando.set(false);
      }
    });
  }
}
