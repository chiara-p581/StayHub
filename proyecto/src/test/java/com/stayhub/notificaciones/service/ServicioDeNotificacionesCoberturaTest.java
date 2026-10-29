package com.stayhub.notificaciones.service;

import com.stayhub.notificaciones.client.NotificadorCanal;
import com.stayhub.notificaciones.dto.CanalNotificacion;
import com.stayhub.notificaciones.dto.ResultadoEnvioNotificacionDTO;
import com.stayhub.notificaciones.dto.SolicitudNotificacionDTO;
import com.stayhub.notificaciones.dto.TipoEvento;
import com.stayhub.notificaciones.exception.CodigoErrorNotificacion;
import com.stayhub.notificaciones.exception.NotificacionException;
import com.stayhub.seguridad.SeguridadException;
import jakarta.enterprise.inject.Instance;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Validaciones, cuota y manejo de errores de ServicioDeNotificacionesImpl. */
class ServicioDeNotificacionesCoberturaTest {

    private ServicioDeNotificacionesImpl servicio;
    private NotificadorCanal email;
    private NotificadorCanal sms;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        email = mock(NotificadorCanal.class);
        sms = mock(NotificadorCanal.class);
        when(email.canal()).thenReturn(CanalNotificacion.EMAIL);
        when(sms.canal()).thenReturn(CanalNotificacion.SMS);
        Instance<NotificadorCanal> instancias = mock(Instance.class);
        when(instancias.iterator()).thenAnswer(i -> List.of(sms, email).iterator());
        servicio = new ServicioDeNotificacionesImpl();
        servicio.notificadores = instancias;
    }

    /** Destinatario único para no compartir la cuota estática entre tests. */
    private static String unico() {
        return UUID.randomUUID() + "@test.com";
    }

    private static SolicitudNotificacionDTO solicitud(String destino, TipoEvento tipo, CanalNotificacion canal,
                                                     String asunto, String mensaje) {
        return new SolicitudNotificacionDTO(destino, tipo, canal, asunto, mensaje);
    }

    @Test
    void envioExitosoUsaElNotificadorDelCanalYDevuelveElResultado() {
        var s = solicitud(unico(), TipoEvento.CONFIRMACION_RESERVA, CanalNotificacion.EMAIL, "Asunto", "Mensaje");

        ResultadoEnvioNotificacionDTO r = servicio.enviar(s);

        assertTrue(r.enviado());
        assertEquals(CanalNotificacion.EMAIL, r.canalUtilizado());
        assertNull(r.mensajeError());
        assertNotNull(r.fecha());
        verify(email).enviar(s);
        verify(sms, never()).enviar(any());
    }

    @Test
    void solicitudNulaSeRechazaConSolicitudInvalida() {
        NotificacionException ex = assertThrows(NotificacionException.class, () -> servicio.enviar(null));
        assertEquals(CodigoErrorNotificacion.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(email, sms);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void destinatarioVacioSeRechaza(String destino) {
        var s = solicitud(destino, TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL, "A", "M");
        NotificacionException ex = assertThrows(NotificacionException.class, () -> servicio.enviar(s));
        assertEquals(CodigoErrorNotificacion.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(email);
    }

    @Test
    void canalNuloSeRechaza() {
        var s = solicitud(unico(), TipoEvento.AVISO_PAGO, null, "A", "M");
        NotificacionException ex = assertThrows(NotificacionException.class, () -> servicio.enviar(s));
        assertEquals(CodigoErrorNotificacion.SOLICITUD_INVALIDA, ex.getCodigo());
    }

    @Test
    void tipoDeEventoNuloSeRechaza() {
        var s = solicitud(unico(), null, CanalNotificacion.EMAIL, "A", "M");
        NotificacionException ex = assertThrows(NotificacionException.class, () -> servicio.enviar(s));
        assertEquals(CodigoErrorNotificacion.SOLICITUD_INVALIDA, ex.getCodigo());
        verifyNoInteractions(email);
    }

    @Test
    void destinatarioDeMasDe120CaracteresSeRechazaConError400() {
        var s = solicitud("a".repeat(121), TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL, "A", "M");
        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.enviar(s));
        assertEquals(400, ex.getEstado());
        verifyNoInteractions(email);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void asuntoVacioSeRechaza(String asunto) {
        var s = solicitud(unico(), TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL, asunto, "M");
        assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.enviar(s)).getEstado());
        verifyNoInteractions(email);
    }

    @Test
    void asuntoDeMasDe200CaracteresSeRechazaPeroElLimiteExactoPasa() {
        var largo = solicitud(unico(), TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL, "a".repeat(201), "M");
        assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.enviar(largo)).getEstado());

        var limite = solicitud(unico(), TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL, "a".repeat(200), "M");
        assertTrue(servicio.enviar(limite).enviado());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void mensajeVacioSeRechaza(String mensaje) {
        var s = solicitud(unico(), TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL, "A", mensaje);
        assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.enviar(s)).getEstado());
        verifyNoInteractions(email);
    }

    @Test
    void mensajeDeMasDe4000CaracteresSeRechazaPeroElLimiteExactoPasa() {
        var largo = solicitud(unico(), TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL, "A", "m".repeat(4001));
        assertEquals(400, assertThrows(SeguridadException.class, () -> servicio.enviar(largo)).getEstado());

        var limite = solicitud(unico(), TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL, "A", "m".repeat(4000));
        assertTrue(servicio.enviar(limite).enviado());
    }

    @Test
    void fallaDelNotificadorSeEnvuelveConErrorEnvioYCausa() {
        var s = solicitud(unico(), TipoEvento.AVISO_PAGO, CanalNotificacion.EMAIL, "A", "M");
        RuntimeException causa = new IllegalStateException("SMTP caído");
        doThrow(causa).when(email).enviar(s);

        NotificacionException ex = assertThrows(NotificacionException.class, () -> servicio.enviar(s));

        assertEquals(CodigoErrorNotificacion.ERROR_ENVIO, ex.getCodigo());
        assertSame(causa, ex.getCause());
        assertTrue(ex.getMessage().contains("EMAIL"));
    }

    @Test
    void canalSinNotificadorFallaConCanalNoSoportado() {
        var s = solicitud(unico(), TipoEvento.AVISO_PAGO, CanalNotificacion.PUSH, "A", "M");
        NotificacionException ex = assertThrows(NotificacionException.class, () -> servicio.enviar(s));
        assertEquals(CodigoErrorNotificacion.CANAL_NO_SOPORTADO, ex.getCodigo());
        assertTrue(ex.getMessage().contains("PUSH"));
    }

    @Test
    void sextoEnvioAlMismoDestinatarioYCanalExcedeLaCuota() {
        String destino = unico();
        var s = solicitud(destino, TipoEvento.RECORDATORIO, CanalNotificacion.EMAIL, "A", "M");
        for (int i = 0; i < 5; i++) assertTrue(servicio.enviar(s).enviado());

        SeguridadException ex = assertThrows(SeguridadException.class, () -> servicio.enviar(s));

        assertEquals(429, ex.getEstado());
        verify(email, times(5)).enviar(s);
    }

    @Test
    void laCuotaIgnoraMayusculasDelDestinatarioPeroDistingueElCanal() {
        String destino = unico();
        for (int i = 0; i < 5; i++) {
            servicio.enviar(solicitud(destino.toUpperCase(), TipoEvento.RECORDATORIO, CanalNotificacion.EMAIL, "A", "M"));
        }
        var mismaPersonaEnMinusculas = solicitud(destino, TipoEvento.RECORDATORIO, CanalNotificacion.EMAIL, "A", "M");
        assertEquals(429, assertThrows(SeguridadException.class, () -> servicio.enviar(mismaPersonaEnMinusculas)).getEstado());

        var otroCanal = solicitud(destino, TipoEvento.RECORDATORIO, CanalNotificacion.SMS, "A", "M");
        assertTrue(servicio.enviar(otroCanal).enviado());
        verify(sms).enviar(otroCanal);
    }
}
