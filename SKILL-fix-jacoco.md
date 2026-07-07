SKILL: Reparar cobertura JaCoCo
Te paso el % actual y la clase/paquete señalado por el reporte.
1. Identificar líneas/branches NO cubiertos (no adivinar, pedir el reporte si hace falta).
2. Generar SOLO los tests faltantes (casos de error, branches condicionales, excepciones).
3. No tocar código de producción salvo que el test revele un bug real.
4. Confirmar mentalmente que el nuevo test sube el % objetivo antes de entregarlo.