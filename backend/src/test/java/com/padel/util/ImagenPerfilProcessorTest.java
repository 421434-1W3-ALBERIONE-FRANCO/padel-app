package com.padel.util;

import com.padel.exception.DatosInvalidosException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

public class ImagenPerfilProcessorTest {

    private final ImagenPerfilProcessor processor = new ImagenPerfilProcessor();

    public static byte[] imagen(int ancho, int alto, String formato) throws IOException {
        BufferedImage img = new BufferedImage(ancho, alto, formato.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 160, 90));
        g.fillRect(0, 0, ancho, alto);
        g.setColor(Color.ORANGE);
        g.fillOval(ancho / 4, alto / 4, ancho / 2, alto / 2);
        g.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(img, formato, bytes);
        return bytes.toByteArray();
    }

    private static BufferedImage leer(byte[] jpeg) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    @Test
    void recortaAlCuadradoYLlevaA512() throws IOException {
        byte[] salida = processor.normalizar(imagen(1600, 900, "png"));

        // Arranca con la marca de un JPEG (FF D8) aunque haya entrado un PNG.
        assertEquals((byte) 0xFF, salida[0]);
        assertEquals((byte) 0xD8, salida[1]);
        BufferedImage resultado = leer(salida);
        assertEquals(512, resultado.getWidth());
        assertEquals(512, resultado.getHeight());
    }

    @Test
    void unaFotoChicaNoSeAgranda() throws IOException {
        BufferedImage resultado = leer(processor.normalizar(imagen(300, 200, "jpg")));

        assertEquals(200, resultado.getWidth());
        assertEquals(200, resultado.getHeight());
    }

    @Test
    void rechazaLoQueNoEsUnaImagenOEsDeOtroFormato() throws IOException {
        assertThrows(DatosInvalidosException.class, () -> processor.normalizar("no soy una foto".getBytes(StandardCharsets.UTF_8)));
        assertThrows(DatosInvalidosException.class, () -> processor.normalizar(new byte[0]));
        assertThrows(DatosInvalidosException.class, () -> processor.normalizar(imagen(200, 200, "gif")));
    }

    @Test
    void rechazaUnaFotoDemasiadoChica() {
        assertThrows(DatosInvalidosException.class, () -> processor.normalizar(imagen(40, 40, "png")));
    }

    @Test
    void rechazaUnaImagenDeDimensionesEnormesSinDescomprimirla() throws IOException {
        // Encabezado PNG que declara 50000x50000 (2.500 millones de píxeles) en unos pocos bytes.
        byte[] bomba = encabezadoPng(50_000, 50_000);

        DatosInvalidosException error = assertThrows(DatosInvalidosException.class, () -> processor.normalizar(bomba));
        assertTrue(error.getMessage().contains("píxeles"));
    }

    private static byte[] encabezadoPng(int ancho, int alto) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream datos = new DataOutputStream(ihdr);
        datos.writeBytes("IHDR");
        datos.writeInt(ancho);
        datos.writeInt(alto);
        datos.write(new byte[]{8, 2, 0, 0, 0}); // 8 bits, RGB, sin entrelazado
        byte[] chunk = ihdr.toByteArray();
        out.writeInt(chunk.length - 4);
        out.write(chunk);
        CRC32 crc = new CRC32();
        crc.update(chunk);
        out.writeInt((int) crc.getValue());
        return bytes.toByteArray();
    }
}
