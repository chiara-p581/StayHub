package com.stayhub.seguridad;

import java.util.List;

public record PaginaResponse<T>(List<T> items, long total, int offset, int limite) { }
