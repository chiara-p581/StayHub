package com.stayhub.seguridad;
import jakarta.servlet.http.*;
import java.util.*;
import static org.mockito.Mockito.*;

public final class IdentidadFixture {
    public static void campo(Object obj, String campo, Object valor) throws Exception {
        var f = obj.getClass().getDeclaredField(campo); f.setAccessible(true); f.set(obj, valor);
    }
    public static HttpSession sesion(Long id, String rol) {
        HttpSession session = mock(HttpSession.class);
        Map<String,Object> attrs = new HashMap<>(); if (id != null) attrs.put("usuarioId", id); if (rol != null) attrs.put("usuarioRol",rol);
        when(session.getAttribute(anyString())).thenAnswer(i -> attrs.get(i.getArgument(0)));
        doAnswer(i -> {attrs.put(i.getArgument(0),i.getArgument(1));return null;}).when(session).setAttribute(anyString(),any());
        return session;
    }
    public static IdentidadActual usuario(Long id, String rol) throws Exception {
        var request = mock(HttpServletRequest.class); var session = sesion(id,rol); when(request.getSession(false)).thenReturn(session);
        var identidad = new IdentidadActual(); campo(identidad,"request",request);return identidad;
    }
    public static IdentidadActual pms(Set<Long> hoteles) throws Exception {
        var request = mock(HttpServletRequest.class);when(request.getAttribute(IdentidadActual.PMS_HOTELES)).thenReturn(hoteles);
        var identidad=new IdentidadActual();campo(identidad,"request",request);return identidad;
    }
}
