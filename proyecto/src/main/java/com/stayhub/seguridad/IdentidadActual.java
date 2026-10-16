package com.stayhub.seguridad;

import com.stayhub.reservas.model.Reserva;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** La identidad viene del contenedor HTTP, nunca de parámetros del cliente. */
@RequestScoped
public class IdentidadActual {
    public static final String PMS_HOTELES = "stayhub.pms.hoteles.autorizados";
    @Inject private HttpServletRequest request;
    public Long usuarioId() {
        HttpSession s = request.getSession(false);
        Long id = s == null ? null : (Long) s.getAttribute("usuarioId");
        if (id == null) throw new SeguridadException(401, "Iniciá sesión para continuar");
        return id;
    }
    public boolean admin() {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute("usuarioId") != null && "ADMIN".equals(s.getAttribute("usuarioRol"));
    }
    public boolean pms() { return request.getAttribute(PMS_HOTELES) instanceof java.util.Set<?>; }
    public void exigirHuesped() {
        usuarioId();
        HttpSession s = request.getSession(false);
        if (!"HUESPED".equals(s.getAttribute("usuarioRol"))) throw new SeguridadException(403, "Solo huéspedes pueden reservar o pagar");
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
