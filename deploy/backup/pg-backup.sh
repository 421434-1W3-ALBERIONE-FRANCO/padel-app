#!/bin/sh
# Backup periódico de la base para el servicio "backup" de docker-compose.yml.
# Usa las variables estándar de libpq (PGHOST, PGDATABASE, PGUSER, PGPASSWORD). Cada dump se escribe primero
# en un .tmp y se renombra al terminar, así un backup cortado a la mitad nunca parece válido.
set -u

intervalo="${BACKUP_INTERVAL_SECONDS:-86400}"
retencion="${BACKUP_RETENTION_DAYS:-14}"

while true; do
  archivo="/backups/${PGDATABASE}-$(date +%Y%m%d-%H%M%S).dump"
  if pg_dump -Fc -f "${archivo}.tmp"; then
    mv "${archivo}.tmp" "${archivo}"
    echo "Backup generado: ${archivo}"
  else
    rm -f "${archivo}.tmp"
    echo "Falló el backup de ${PGDATABASE}" >&2
  fi
  # Retención: se borran solo los dumps más viejos que BACKUP_RETENTION_DAYS días.
  find /backups -name '*.dump' -type f -mtime +"${retencion}" -delete
  sleep "${intervalo}"
done
