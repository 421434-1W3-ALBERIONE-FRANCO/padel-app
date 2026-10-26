package com.padel.domain.entity;

import com.padel.exception.DatosInvalidosException;
import com.padel.exception.StockInsuficienteException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 50)
    private String categoria;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private boolean activo;

    public void descontarStock(int cantidad) {
        if (cantidad <= 0) {
            throw new DatosInvalidosException("La cantidad debe ser mayor a cero");
        }
        if (stock < cantidad) {
            throw new StockInsuficienteException("Stock insuficiente para el producto: " + nombre + ". Stock disponible: " + stock);
        }
        stock -= cantidad;
    }
}
