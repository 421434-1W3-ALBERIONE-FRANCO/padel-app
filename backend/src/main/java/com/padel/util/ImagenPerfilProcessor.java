package com.padel.util;

import com.padel.exception.DatosInvalidosException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Set;

/**
 * Normaliza la foto de perfil: la decodifica (si no es una imagen real, se rechaza), la recorta al cuadrado del
 * centro, la lleva a {@value #LADO}px y la vuelve a codificar como JPEG. Al re-codificar se descartan los
 * metadatos (EXIF con ubicación GPS, modelo del teléfono, etc.).
 */
@Component
public class ImagenPerfilProcessor {

    public static final int LADO = 512;
    public static final String TIPO = "image/jpeg";

    private static final int LADO_MINIMO = 64;
    // Se valida con las dimensiones del encabezado, antes de decodificar: una imagen chica en bytes pero de
    // dimensiones enormes ocuparía gigas en memoria al descomprimirse.
    private static final long PIXELES_MAXIMOS = 40_000_000L;
    private static final Set<String> FORMATOS = Set.of("jpeg", "jpg", "png");
    private static final float CALIDAD_JPEG = 0.85f;

    public byte[] normalizar(byte[] datos) {
        if (datos == null || datos.length == 0) {
            throw new DatosInvalidosException("La foto está vacía");
        }
        BufferedImage original = leer(datos);
        BufferedImage cuadrada = recortarYEscalar(original);
        return escribirJpeg(cuadrada);
    }

    private BufferedImage leer(byte[] datos) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(datos))) {
            Iterator<ImageReader> lectores = entrada == null ? null : ImageIO.getImageReaders(entrada);
            if (lectores == null || !lectores.hasNext()) {
                throw new DatosInvalidosException("El archivo no es una imagen válida: subí una foto JPG o PNG");
            }
            ImageReader lector = lectores.next();
            try {
                if (!FORMATOS.contains(lector.getFormatName().toLowerCase())) {
                    throw new DatosInvalidosException("Formato no admitido: subí una foto JPG o PNG");
                }
                lector.setInput(entrada, true, true);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if ((long) ancho * alto > PIXELES_MAXIMOS) {
                    throw new DatosInvalidosException("La foto tiene demasiados píxeles");
                }
                if (ancho < LADO_MINIMO || alto < LADO_MINIMO) {
                    throw new DatosInvalidosException("La foto es demasiado chica (mínimo " + LADO_MINIMO + "x" + LADO_MINIMO + ")");
                }
                return lector.read(0);
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw new DatosInvalidosException("No se pudo leer la foto: subí una foto JPG o PNG");
        }
    }

    private BufferedImage recortarYEscalar(BufferedImage original) {
        int lado = Math.min(original.getWidth(), original.getHeight());
        int x = (original.getWidth() - lado) / 2;
        int y = (original.getHeight() - lado) / 2;
        BufferedImage actual = original.getSubimage(x, y, lado, lado);

        // Achica a la mitad mientras sobre: un solo salto grande con bicúbico deja la imagen serruchada.
        int tamano = lado;
        while (tamano / 2 >= LADO) {
            tamano /= 2;
            actual = dibujar(actual, tamano);
        }
        return dibujar(actual, Math.min(tamano, LADO));
    }

    /** Copia a un lienzo RGB opaco (fondo blanco para los PNG con transparencia) del tamaño indicado. */
    private BufferedImage dibujar(BufferedImage origen, int tamano) {
        BufferedImage destino = new BufferedImage(tamano, tamano, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, tamano, tamano);
            g.drawImage(origen, 0, 0, tamano, tamano, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private byte[] escribirJpeg(BufferedImage imagen) {
        ImageWriter escritor = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream salida = ImageIO.createImageOutputStream(bytes)) {
            escritor.setOutput(salida);
            ImageWriteParam parametros = escritor.getDefaultWriteParam();
            parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parametros.setCompressionQuality(CALIDAD_JPEG);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo codificar la foto de perfil", e);
        } finally {
            escritor.dispose();
        }
        return bytes.toByteArray();
    }
}
