package com.stayhub.notificaciones.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Comportamiento de PlantillaEmail: armado del HTML, escape, URLs seguras, texto plano y formatos. */
class PlantillaEmailCoberturaTest {

    private static Map<String, String> detalles() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Hotel", "Sol");
        m.put("Noches", "2");
        return m;
    }

    @Test
    void armaCorreoCompletoConTodasLasSecciones() {
        String html = PlantillaEmail.armar("Reserva confirmada", "Hola Ana,", "Tu reserva está lista.", detalles(),
                "Ver reserva", "https://stayhub.example/r/1", "Guardá este correo.");

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<title>Reserva confirmada</title>"));
        assertTrue(html.contains(">Reserva confirmada</h1>"));
        assertTrue(html.contains(">Hola Ana,</p>"));
        assertTrue(html.contains(">Tu reserva está lista.</p>"));
        assertTrue(html.contains(">Hotel</td>"));
        assertTrue(html.contains(">Sol</td>"));
        assertTrue(html.contains(">Noches</td>"));
        assertTrue(html.contains("href=\"https://stayhub.example/r/1\""));
        assertTrue(html.contains(">Ver reserva</a>"));
        assertTrue(html.contains(">Guardá este correo.</p>"));
        assertTrue(html.contains("StayHub"));
        assertTrue(html.endsWith("</body></html>"));
        assertTrue(html.indexOf("Hotel") < html.indexOf("Noches"), "los detalles respetan el orden de inserción");
    }

    @Test
    void sinSaludoDetallesBotonNiNotaOmiteEsasSecciones() {
        String html = PlantillaEmail.armar("Aviso", null, "Texto", null, null, null, null);

        assertTrue(html.contains(">Texto</p>"));
        assertFalse(html.contains("font-size:16px;\">null"));
        assertFalse(html.contains("<a href"));
        assertFalse(html.contains("border-left:4px"));
        assertFalse(html.contains("font-size:13px"));
        assertEquals(1, html.split("<p style", -1).length - 1, "solo el párrafo principal");
    }

    @Test
    void detallesVaciosNoDibujanLaTarjeta() {
        String html = PlantillaEmail.armar("Aviso", null, "Texto", Map.of(), null, null, null);
        assertFalse(html.contains("border-left:4px"));
    }

    @Test
    void valorNuloEnDetallesSeRenderizaVacio() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("Código", null);
        String html = PlantillaEmail.armar("Aviso", null, "Texto", m, null, null, null);
        assertTrue(html.contains(">Código</td>"));
        assertFalse(html.contains("null"));
    }

    @Test
    void botonSinUrlOSinTextoNoSeDibuja() {
        assertFalse(PlantillaEmail.armar("T", null, "P", null, "Ir", null, null).contains("<a href"));
        assertFalse(PlantillaEmail.armar("T", null, "P", null, null, "https://x.com", null).contains("<a href"));
    }

    @Test
    void escapaHtmlEnTodosLosCamposDeTexto() {
        Map<String, String> m = Map.of("<k>", "a&b\"c");
        String html = PlantillaEmail.armar("<script>", "<b>Hola</b>", "x & y", m, "<i>Ir</i>", "https://ok.com", "<n>");

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("&lt;b&gt;Hola&lt;/b&gt;"));
        assertTrue(html.contains("x &amp; y"));
        assertTrue(html.contains("&lt;k&gt;"));
        assertTrue(html.contains("a&amp;b&quot;c"));
        assertTrue(html.contains("&lt;i&gt;Ir&lt;/i&gt;"));
        assertTrue(html.contains("&lt;n&gt;"));
    }

    @Test
    void parrafoNuloSeRenderizaVacioSinFallar() {
        String html = PlantillaEmail.armar("T", null, null, null, null, null, null);
        assertTrue(html.contains("line-height:1.6;\"></p>"));
    }

    @Test
    void urlPeligrosaDelBotonSeReemplazaPorNumeral() {
        String html = PlantillaEmail.armar("T", null, "P", null, "Ir", "javascript:alert(1)", null);
        assertTrue(html.contains("href=\"#\""));
        assertFalse(html.contains("javascript"));
    }

    @Test
    void genericoQuitaElSufijoDeMarcaDelAsunto() {
        String html = PlantillaEmail.generico("Cambio en tu reserva — StayHub", "Detalle");
        assertTrue(html.contains("<title>Cambio en tu reserva</title>"));
        assertTrue(html.contains(">Detalle</p>"));
        assertFalse(html.contains("<a href"));
    }

    @Test
    void genericoSinAsuntoUsaLaMarca() {
        assertTrue(PlantillaEmail.generico(null, "Texto").contains("<title>StayHub</title>"));
    }

    @Test
    void esHtmlDetectaTextoQueEmpiezaConMenorQue() {
        assertTrue(PlantillaEmail.esHtml("<p>hola</p>"));
        assertTrue(PlantillaEmail.esHtml("  \n <div>"));
        assertFalse(PlantillaEmail.esHtml("hola <b>"));
        assertFalse(PlantillaEmail.esHtml(""));
        assertFalse(PlantillaEmail.esHtml(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://stayhub.example/a", "HTTPS://stayhub.example/a", "https://stayhub.example:8443/a?x=1"})
    void urlSeguraAceptaHttpYHttpsConHost(String url) {
        assertEquals(url, PlantillaEmail.urlSegura(url));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://stayhub.example/a", "/ruta/relativa", "https:///sin-host", "mailto:a@b.com",
            "http://u@stayhub.example/", "no es una url", "https://exa mple.com"})
    void urlSeguraRechazaEsquemasHostsVaciosCredencialesYSintaxisInvalida(String url) {
        assertEquals("#", PlantillaEmail.urlSegura(url));
    }

    @Test
    void aTextoPlanoConvierteEnlacesSaltosYEntidades() {
        String html = "<html><head><title>ocultar</title></head><body><h1>Título</h1><p>Hola &amp; chau &lt;b&gt; &quot;x&quot;</p>"
                + "<table><tr><td>Hotel</td><td>Sol</td></tr></table>"
                + "<p>Línea<br>otra<br/>más</p><a href=\"https://x.com/a\" style=\"c\">Entrar</a></body></html>";

        String texto = PlantillaEmail.aTextoPlano(html);

        assertFalse(texto.contains("ocultar"));
        assertFalse(texto.contains("<td") || texto.contains("<p>") || texto.contains("<br"), texto);
        assertTrue(texto.contains("Título"));
        assertTrue(texto.contains("Hola & chau <b> \"x\""));
        assertTrue(texto.contains("Hotel  Sol"));
        assertTrue(texto.contains("Línea\notra\nmás"));
        assertTrue(texto.contains("Entrar: https://x.com/a"));
    }

    @Test
    void aTextoPlanoColapsaLineasVaciasYRecortaLosExtremos() {
        String texto = PlantillaEmail.aTextoPlano("\n\n<p>A</p><p></p><p></p><p></p><p>B</p>  \n");
        assertEquals("A\n\nB", texto);
    }

    @Test
    void aTextoPlanoDeUnCorreoArmadoConservaElContenido() {
        String html = PlantillaEmail.armar("Pago", "Hola,", "Gracias.", detalles(), "Ver", "https://s.com/x", "Nota");
        String texto = PlantillaEmail.aTextoPlano(html);
        assertTrue(texto.contains("Pago"));
        assertTrue(texto.contains("Gracias."));
        assertTrue(texto.contains("Ver: https://s.com/x"));
        assertTrue(texto.contains("Nota"));
        assertTrue(texto.contains("no lo respondas"));
    }

    @Test
    void fechaNulaSeMuestraComoGuion() {
        assertEquals("-", PlantillaEmail.fecha(null));
    }

    @Test
    void fechaSeFormateaEnEspanolRioplatense() {
        String f = PlantillaEmail.fecha(LocalDate.of(2026, 12, 1));
        assertTrue(f.contains("1 de diciembre de 2026"), f);
    }

    @Test
    void montoConMonedaUsaSeparadoresArgentinosYDosDecimales() {
        assertEquals("ARS 1.234,50", PlantillaEmail.monto(new BigDecimal("1234.5"), "ARS"));
        assertEquals("USD 10,00", PlantillaEmail.monto(BigDecimal.TEN, "USD"));
        assertEquals("USD 0,13", PlantillaEmail.monto(new BigDecimal("0.126"), "USD"));
    }

    @Test
    void montoSinMonedaOSinImporte() {
        assertEquals("5,00", PlantillaEmail.monto(new BigDecimal("5"), null));
        assertEquals("ARS -", PlantillaEmail.monto(null, "ARS"));
        assertEquals("-", PlantillaEmail.monto(null, null));
    }
}
