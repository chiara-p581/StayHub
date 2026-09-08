package com.stayhub.canalesexternos.service;

import com.stayhub.canalesexternos.dto.Canal;
import com.stayhub.canalesexternos.dto.ReservaExternaDTO;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeguridadCanalesTest {

    @Test
    void declaraLosRolesDelComponente() {
        DeclareRoles roles = ServicioDeCanalesExternosImpl.class.getAnnotation(DeclareRoles.class);

        assertNotNull(roles);
        assertEquals(Set.of("ADMIN", "CANAL_EXTERNO"), Set.of(roles.value()));
    }

    @Test
    void operacionesAdministrativasExigenAdmin() throws Exception {
        assertRoles("consultarDisponibilidad", new Class<?>[]{Long.class, LocalDate.class, LocalDate.class}, "ADMIN");
        assertRoles("sincronizarOta", new Class<?>[]{Long.class, Canal.class, LocalDate.class, LocalDate.class}, "ADMIN");
        assertRoles("sincronizarPms", new Class<?>[]{Long.class, LocalDate.class, LocalDate.class}, "ADMIN");
    }

    @Test
    void webhooksOtaPermitenCuentaTecnicaOAdmin() throws Exception {
        assertRoles("recibirReserva", new Class<?>[]{ReservaExternaDTO.class}, "ADMIN", "CANAL_EXTERNO");
        assertRoles("modificarReserva", new Class<?>[]{ReservaExternaDTO.class}, "ADMIN", "CANAL_EXTERNO");
        assertRoles("cancelarReserva", new Class<?>[]{Canal.class, String.class}, "ADMIN", "CANAL_EXTERNO");
    }

    private void assertRoles(String metodo, Class<?>[] parametros, String... esperados) throws Exception {
        RolesAllowed roles = ServicioDeCanalesExternosImpl.class
                .getMethod(metodo, parametros)
                .getAnnotation(RolesAllowed.class);

        assertNotNull(roles, "Falta @RolesAllowed en " + metodo);
        assertEquals(Set.of(esperados), Set.of(roles.value()));
    }
}
