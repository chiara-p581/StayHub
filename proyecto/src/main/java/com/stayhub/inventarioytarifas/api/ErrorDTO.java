package com.stayhub.inventarioytarifas.api;

import java.time.OffsetDateTime;

public record ErrorDTO(String codigo, String mensaje, OffsetDateTime fecha) { }
