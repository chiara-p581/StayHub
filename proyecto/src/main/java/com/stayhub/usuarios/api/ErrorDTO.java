package com.stayhub.usuarios.api;

import java.time.OffsetDateTime;

public record ErrorDTO(String codigo, String mensaje, OffsetDateTime fecha) { }