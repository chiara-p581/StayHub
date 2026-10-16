package com.stayhub.seguridad;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;

public final class CuerpoSeguro {
    public static final int MAX_BYTES = 65536;
    private CuerpoSeguro() {}
    public static byte[] leer(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new SeguridadException(413, "El cuerpo supera 64 KiB");
        return bytes;
    }
    public static void json(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        int profundidad = 0; boolean cadena = false, escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (cadena) { if (escape) escape = false; else if (c == '\\') escape = true; else if (c == '"') cadena = false; continue; }
            if (c == '"') cadena = true;
            else if (c == '[' || c == '{') { if (++profundidad > 20) throw new SeguridadException(400, "JSON demasiado profundo"); }
            else if (c == ']' || c == '}') profundidad--;
            else if (c == '-' || Character.isDigit(c)) {
                int start = i;
                while (i + 1 < text.length() && "0123456789.eE+-".indexOf(text.charAt(i + 1)) >= 0) i++;
                try {
                    String numero = text.substring(start, i + 1);
                    if (numero.length() > 32) throw new NumberFormatException();
                    BigDecimal n = new BigDecimal(numero);
                    if (n.precision() > 19 || n.scale() < 0 || n.scale() > 4) throw new NumberFormatException();
                } catch (NumberFormatException ex) { throw new SeguridadException(400, "Número JSON fuera de los límites permitidos"); }
            }
        }
    }
    public static void xml(byte[] bytes) {
        try {
            SAXParserFactory f = SAXParserFactory.newInstance();
            f.setNamespaceAware(true); f.setXIncludeAware(false);
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            XMLReader reader = f.newSAXParser().getXMLReader();
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            reader.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            reader.setEntityResolver((publicId, systemId) -> { throw new SAXException("Entidad externa prohibida"); });
            reader.setContentHandler(new DefaultHandler() {
                int profundidad, elementos;
                public void startElement(String uri, String local, String q, Attributes attrs) throws SAXException {
                    if (++profundidad > 32 || ++elementos > 5000) throw new SAXException("XML demasiado complejo");
                }
                public void endElement(String uri, String local, String q) { profundidad--; }
            });
            reader.setErrorHandler(new DefaultHandler() { public void fatalError(SAXParseException ex) throws SAXException { throw ex; } });
            reader.parse(new InputSource(new ByteArrayInputStream(bytes)));
        } catch (Exception ex) { throw new SeguridadException(400, "XML inválido o inseguro"); }
    }
}
