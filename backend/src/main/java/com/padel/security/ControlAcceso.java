package com.padel.security;

import com.padel.domain.entity.Usuario;
import com.padel.domain.enums.RolUsuario;
import com.padel.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Reglas de ownership compartidas por los servicios. Antes cada servicio repetía
 * {@code isAdminOrRecepcion && !dueño}; ahora la regla vive en un solo lugar y se compara siempre por id.
 */
@Component
public class ControlAcceso {

    public boolean esStaff(Usuario usuario) {
        return usuario.getRol() == RolUsuario.ADMIN || usuario.getRol() == RolUsuario.RECEPCIONISTA;
    }

    public boolean esDueno(Usuario usuario, Usuario dueno) {
        return dueno != null && dueno.getId() != null && dueno.getId().equals(usuario.getId());
    }

    /**
     * Permite al dueño del recurso y al staff. Para el resto responde 404 (no 403) a propósito: no confirma
     * que exista un recurso ajeno con ese id.
     */
    public void exigirDuenoOStaff(Usuario usuario, Usuario dueno, String recurso) {
        if (!esStaff(usuario) && !esDueno(usuario, dueno)) {
            throw new ResourceNotFoundException(recurso + " no encontrado para el usuario especificado");
        }
    }
}
