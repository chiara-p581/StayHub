package com.stayhub.notificaciones.service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Arma el HTML de todos los emails de StayHub con un mismo diseño
 * (encabezado con la marca, tarjeta con detalles, botón y pie).
 * Usa tablas y estilos en línea porque es lo único que respetan
 * todos los clientes de correo (Gmail, Outlook, Apple Mail).
 */
public final class PlantillaEmail {

    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("EEE d 'de' MMMM 'de' yyyy", ES_AR);

    private static final String COLOR_MARCA = "#0f4c5c";
    private static final String COLOR_ACENTO = "#e9c46a";
    private static final String COLOR_TEXTO = "#1f2937";
    private static final String COLOR_SUAVE = "#6b7280";
    private static final String COLOR_FONDO = "#f3f4f6";

    private PlantillaEmail() { }

    /**
     * @param titulo     título grande del mail
     * @param saludo     primera línea (ej. "Hola Matilda,"); puede ser null
     * @param parrafo    texto principal
     * @param detalles   filas "etiqueta → valor" para la tarjeta; puede ser null o vacío
     * @param textoBoton texto del botón; null si no lleva botón
     * @param urlBoton   link del botón
     * @param nota       texto chico debajo del botón; puede ser null
     */
    public static String armar(String titulo, String saludo, String parrafo, Map<String, String> detalles,
                               String textoBoton, String urlBoton, String nota) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            .append("<title>").append(esc(titulo)).append("</title></head>")
            .append("<body style=\"margin:0;padding:0;background:").append(COLOR_FONDO).append(";\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:")
            .append(COLOR_FONDO).append(";padding:32px 12px;\"><tr><td align=\"center\">")
            .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:600px;")
            .append("background:#ffffff;border-radius:14px;overflow:hidden;font-family:Arial,Helvetica,sans-serif;color:")
            .append(COLOR_TEXTO).append(";\">");

        // Encabezado con la marca
        html.append("<tr><td style=\"background:").append(COLOR_MARCA).append(";padding:24px 32px;\">")
            .append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
            .append("<td style=\"width:36px;height:36px;background:").append(COLOR_ACENTO)
            .append(";border-radius:8px;text-align:center;font-size:20px;font-weight:bold;color:")
            .append(COLOR_MARCA).append(";\">S</td>")
            .append("<td style=\"padding-left:12px;font-size:22px;font-weight:bold;color:#ffffff;letter-spacing:0.5px;\">StayHub</td>")
            .append("</tr></table></td></tr>");

        // Cuerpo
        html.append("<tr><td style=\"padding:32px;\">")
            .append("<h1 style=\"margin:0 0 16px;font-size:24px;line-height:1.3;color:").append(COLOR_MARCA).append(";\">")
            .append(esc(titulo)).append("</h1>");
        if (saludo != null) {
            html.append("<p style=\"margin:0 0 12px;font-size:16px;\">").append(esc(saludo)).append("</p>");
        }
        html.append("<p style=\"margin:0 0 24px;font-size:16px;line-height:1.6;\">").append(esc(parrafo)).append("</p>");

        // Tarjeta de detalles
        if (detalles != null && !detalles.isEmpty()) {
            html.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"background:#f9fafb;border:1px solid #e5e7eb;border-left:4px solid ").append(COLOR_ACENTO)
                .append(";border-radius:10px;margin:0 0 24px;\">");
            detalles.forEach((etiqueta, valor) -> html
                .append("<tr><td style=\"padding:10px 16px;font-size:14px;color:").append(COLOR_SUAVE).append(";\">")
                .append(esc(etiqueta)).append("</td>")
                .append("<td style=\"padding:10px 16px;font-size:14px;font-weight:bold;text-align:right;\">")
                .append(esc(valor)).append("</td></tr>"));
            html.append("</table>");
        }

        // Botón
        if (textoBoton != null && urlBoton != null) {
            html.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:0 0 24px;\"><tr>")
                .append("<td style=\"background:").append(COLOR_MARCA).append(";border-radius:8px;\">")
                .append("<a href=\"").append(esc(urlBoton)).append("\" style=\"display:inline-block;padding:14px 28px;")
                .append("font-size:16px;font-weight:bold;color:#ffffff;text-decoration:none;\">")
                .append(esc(textoBoton)).append("</a></td></tr></table>");
        }

        if (nota != null) {
            html.append("<p style=\"margin:0;font-size:13px;line-height:1.5;color:").append(COLOR_SUAVE).append(";\">")
                .append(esc(nota)).append("</p>");
        }
        html.append("</td></tr>");

        // Pie
        html.append("<tr><td style=\"padding:20px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;")
            .append("font-size:12px;line-height:1.5;color:").append(COLOR_SUAVE).append(";\">")
            .append("Este es un correo automático de StayHub, por favor no lo respondas.<br>")
            .append("StayHub · Tu próxima estadía, todo en un solo lugar.")
            .append("</td></tr></table></td></tr></table></body></html>");
        return html.toString();
    }

    /** Diseño estándar para mails que llegan como texto simple (por ejemplo, los de overbooking). */
    public static String generico(String asunto, String texto) {
        String titulo = asunto == null ? "StayHub" : asunto.replace(" — StayHub", "");
        return armar(titulo, null, texto, null, null, null, null);
    }

    public static boolean esHtml(String texto) {
        return texto != null && texto.stripLeading().startsWith("<");
    }

    /** Versión en texto plano del HTML, para clientes de correo que no muestran HTML. */
    public static String aTextoPlano(String html) {
        String texto = html
                .replaceAll("(?is)<head.*?</head>", "")
                .replaceAll("(?is)<a [^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>", "$2: $1")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|h1|tr)>", "\n")
                .replaceAll("(?i)</td>", "  ")
                .replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
        return texto.replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").strip();
    }

    public static String fecha(LocalDate fecha) {
        return fecha == null ? "-" : FECHA.format(fecha);
    }

    public static String monto(BigDecimal monto, String moneda) {
        NumberFormat formato = NumberFormat.getNumberInstance(ES_AR);
        formato.setMinimumFractionDigits(2);
        formato.setMaximumFractionDigits(2);
        return (moneda == null ? "" : moneda + " ") + (monto == null ? "-" : formato.format(monto));
    }

    private static String esc(String texto) {
        if (texto == null) return "";
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}