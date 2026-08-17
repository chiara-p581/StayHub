package com.stayhub.canalesexternos.contrato.interno;

/** Contrato que deberá implementar el componente ServicioDeReservas. */
public interface ServicioDeReservasPort {
    ResultadoOperacionReserva crearDesdeCanal(SolicitudReserva solicitud);
    ResultadoOperacionReserva modificarDesdeCanal(SolicitudReserva solicitud);
    ResultadoOperacionReserva cancelarDesdeCanal(String canal, String referenciaExterna);
}
