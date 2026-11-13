package com.stayhub.seguridad;

import com.stayhub.reservas.model.Reserva;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;

/**
 * La identidad viene del JWT validado por {@link MecanismoJwt} (o del certificado del
 * PMS en SOAP), nunca de parámetros del cliente.
 */
@RequestScoped
public class IdentidadActual {
    public static final String PMS_HOTELES = "stayhub.pms.hoteles.autorizados";
    @Inject private HttpServletRequest request;

    public Long usuarioId() {
        CredencialActual credencial = CredencialActual.de(request);
        if (credencial == null) throw new SeguridadException(401, "Iniciá sesión para continuar");
        return credencial.usuarioId();
    }
    public boolean admin() {
        CredencialActual credencial = CredencialActual.de(request);
        return credencial != null && credencial.admin();
    }
    public boolean pms() { return request.getAttribute(PMS_HOTELES) instanceof java.util.Set<?>; }
    public void exigirHuesped() {
        usuarioId();
        if (!Roles.HUESPED.equals(CredencialActual.de(request).rol()))
            throw new SeguridadException(403, "Solo huéspedes pueden reservar o pagar");
    }
    public void autorizarHotel(Long hotelId) {
        Object autorizados = request.getAttribute(PMS_HOTELES);
        if (hotelId == null || !(autorizados instanceof java.util.Set<?> hoteles) || !hoteles.contains(hotelId))
            throw new SeguridadException(403, "Operación PMS no autorizada para este hotel");
    }
    public void autorizarReserva(Reserva reserva) {
        if (pms()) { autorizarHotel(reserva.getHotelId()); return; }
        if (admin()) return;
        if (!usuarioId().equals(reserva.getUsuarioId())) throw new SeguridadException(404, "Reserva no disponible");
    }
    public void autorizarListado(Long hotelId) {
        if (pms()) autorizarHotel(hotelId);
        else if (!admin()) throw new SeguridadException(403, "Esta operación requiere rol ADMIN");
    }
}