package com.stayhub.pagos.client.pasarela;

public record ResultadoCobro(boolean aprobado, String referenciaExterna, String motivoRechazo) { }