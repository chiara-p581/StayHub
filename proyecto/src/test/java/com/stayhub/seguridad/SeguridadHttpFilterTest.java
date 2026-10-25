package com.stayhub.seguridad;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeguridadHttpFilterTest {
    HttpServletRequest req; HttpServletResponse res; FilterChain chain; SeguridadHttpFilter filtro;
    StringWriter body;
    @BeforeEach void preparar() throws Exception {
        req=mock(HttpServletRequest.class);res=mock(HttpServletResponse.class);chain=mock(FilterChain.class);filtro=new SeguridadHttpFilter();body=new StringWriter();
        when(req.getContextPath()).thenReturn("");when(req.getRequestURI()).thenReturn("/api/hoteles");when(req.getMethod()).thenReturn("GET");when(req.isSecure()).thenReturn(true);
        when(req.getRemoteAddr()).thenReturn("10.0.0.2");when(req.getServerName()).thenReturn("stayhub.test");when(req.getServerPort()).thenReturn(443);when(req.getScheme()).thenReturn("https");
        when(res.getWriter()).thenReturn(new PrintWriter(body));
    }
    void cuerpo(String s) throws Exception {
        var input=new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
        when(req.getInputStream()).thenReturn(new ServletInputStream(){public int read(){return input.read();}public boolean isFinished(){return input.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener l){}});
        when(req.getContentType()).thenReturn("application/json");
    }
    @Test void httpNoAccedeAContenidoProtegido() throws Exception {
        when(req.isSecure()).thenReturn(false);filtro.doFilter(req,res,chain);verify(res).setStatus(426);verifyNoInteractions(chain);
        assertTrue(body.toString().contains("correlationId"));
    }
    @Test void solicitudSeguraIncluyeCabeceras() throws Exception {
        filtro.doFilter(req,res,chain);verify(chain).doFilter(req,res);verify(res).setHeader(eq("Content-Security-Policy"),contains("script-src 'self'"));verify(res).setHeader("Strict-Transport-Security","max-age=31536000; includeSubDomains");
        verify(res).setHeader(eq(AuditoriaSeguridad.HEADER_CORRELACION), matches("[0-9a-f-]{36}"));
        verify(req).setAttribute(eq(AuditoriaSeguridad.ATRIBUTO_CORRELACION), matches("[0-9a-f-]{36}"));
    }
    @Test void reemplazaCorrelationIdControladoPorElCliente() throws Exception {
        when(req.getHeader(AuditoriaSeguridad.HEADER_CORRELACION)).thenReturn("pedido-seguro-123");
        filtro.doFilter(req,res,chain);
        verify(res).setHeader(eq(AuditoriaSeguridad.HEADER_CORRELACION), argThat(id -> !"pedido-seguro-123".equals(id)));
        verify(req).setAttribute(eq(AuditoriaSeguridad.ATRIBUTO_CORRELACION), argThat(id -> !"pedido-seguro-123".equals(id)));
    }
    @Test void mutacionSinCsrfNoEjecutaServicio() throws Exception {
        when(req.getMethod()).thenReturn("POST");filtro.doFilter(req,res,chain);verify(res).setStatus(403);verifyNoInteractions(chain);
    }
    @Test void originAjenoFallaAunqueElTokenSeaValido() throws Exception {
        var session=IdentidadFixture.sesion(1L,"HUESPED");when(req.getSession(false)).thenReturn(session);String token=Csrf.token(session);when(req.getHeader("X-CSRF-Token")).thenReturn(token);
        when(req.getHeader("Origin")).thenReturn("https://evil.test");when(req.getMethod()).thenReturn("POST");filtro.doFilter(req,res,chain);verify(res).setStatus(403);verifyNoInteractions(chain);
    }
    @Test void csrfValidoPermiteJsonYConservaCuerpo() throws Exception {
        var session=IdentidadFixture.sesion(1L,"HUESPED");when(req.getSession(false)).thenReturn(session);String token=Csrf.token(session);when(req.getHeader("X-CSRF-Token")).thenReturn(token);when(req.getMethod()).thenReturn("POST");cuerpo("{\"nombre\":\"Hotel\"}");
        doAnswer(i->{var wrapped=(HttpServletRequest)i.getArgument(0);assertEquals("{\"nombre\":\"Hotel\"}",new String(wrapped.getInputStream().readAllBytes(),StandardCharsets.UTF_8));return null;}).when(chain).doFilter(any(),eq(res));
        filtro.doFilter(req,res,chain);verify(chain).doFilter(any(),eq(res));verify(res,never()).setStatus(anyInt());
    }
    @Test void soapSinCertificadoYConSesionAdminSeRechaza() throws Exception {
        when(req.getRequestURI()).thenReturn("/soap/integracion-pms");var session=IdentidadFixture.sesion(1L,"ADMIN");when(req.getSession(false)).thenReturn(session);filtro.doFilter(req,res,chain);verify(res).setStatus(401);verifyNoInteractions(chain);
    }
    @Test void soapCodificadoTambienExigeCertificado() throws Exception {
        when(req.getRequestURI()).thenReturn("/so%61p/integracion-pms");filtro.doFilter(req,res,chain);verify(res).setStatus(401);verifyNoInteractions(chain);
    }
    @Test void certificadoPinneadoEstableceScope() throws Exception {
        byte[] certBytes="cert-prueba".getBytes(StandardCharsets.UTF_8);var cert=mock(X509Certificate.class);when(cert.getEncoded()).thenReturn(certBytes);
        System.setProperty("stayhub.pms.certificado-sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certBytes)));System.setProperty("stayhub.pms.hoteles","1,2");
        try {
            when(req.getRequestURI()).thenReturn("/soap/integracion-pms");when(req.getAttribute("jakarta.servlet.request.X509Certificate")).thenReturn(new X509Certificate[]{cert});filtro.doFilter(req,res,chain);
            verify(req).setAttribute(IdentidadActual.PMS_HOTELES,java.util.Set.of(1L,2L));verify(chain).doFilter(req,res);
        } finally {System.clearProperty("stayhub.pms.certificado-sha256");System.clearProperty("stayhub.pms.hoteles");}
    }
    @Test void contenidoGrandeRechazadoAntesDelParser() throws Exception {
        var session=IdentidadFixture.sesion(1L,"HUESPED");when(req.getSession(false)).thenReturn(session);String token=Csrf.token(session);when(req.getHeader("X-CSRF-Token")).thenReturn(token);when(req.getMethod()).thenReturn("POST");when(req.getContentLengthLong()).thenReturn(100000L);
        filtro.doFilter(req,res,chain);verify(res).setStatus(413);verifyNoInteractions(chain);verify(req,never()).getInputStream();
    }
    @Test void fuerzaBrutaDevuelve429() throws Exception {
        when(req.getRequestURI()).thenReturn("/api/usuarios/login");for(int i=0;i<61;i++)filtro.doFilter(req,res,chain);verify(res).setStatus(429);verify(res).setHeader("Retry-After","900");
    }

    @Test void webhookHmacNoRequiereCsrfNiClaveGlobal() throws Exception {
        when(req.getRequestURI()).thenReturn("/api/canales-externos/otas/BOOKING/reservas");
        when(req.getMethod()).thenReturn("POST");cuerpo("{}");
        filtro.doFilter(req,res,chain);
        verify(chain).doFilter(any(HttpServletRequest.class),eq(res));
        verify(res,never()).setStatus(403);
    }}
