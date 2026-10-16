package com.stayhub.seguridad;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

final class SolicitudConCuerpo extends HttpServletRequestWrapper {
    private final byte[] cuerpo;
    SolicitudConCuerpo(HttpServletRequest request, byte[] cuerpo) { super(request); this.cuerpo = cuerpo; }
    public ServletInputStream getInputStream() {
        ByteArrayInputStream input = new ByteArrayInputStream(cuerpo);
        return new ServletInputStream() {
            public int read() { return input.read(); }
            public boolean isFinished() { return input.available() == 0; }
            public boolean isReady() { return true; }
            public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Lectura síncrona"); }
        };
    }
    public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
}
