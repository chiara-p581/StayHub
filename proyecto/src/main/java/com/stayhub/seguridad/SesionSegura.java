package com.stayhub.seguridad;

public final class SesionSegura {
    public static final String MFA_VERIFICADA = "mfaVerificada";
    public static final String ADMIN_REAUTENTICADO_EN = "adminReautenticadoEn";
    public static final long REAUTENTICACION_ADMIN_MS = 10L * 60 * 1000;
    private SesionSegura() { }
}
