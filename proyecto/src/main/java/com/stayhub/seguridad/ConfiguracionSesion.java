package com.stayhub.seguridad;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import java.util.Set;

@WebListener
public class ConfiguracionSesion implements ServletContextListener {
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        context.setSessionTrackingModes(Set.of(SessionTrackingMode.COOKIE));
        context.setSessionTimeout(15);
        SessionCookieConfig cookie = context.getSessionCookieConfig();
        cookie.setHttpOnly(true);
        cookie.setSecure(!SeguridadHttpFilter.httpLocalHabilitado());
        cookie.setAttribute("SameSite", "Lax");
    }
}
