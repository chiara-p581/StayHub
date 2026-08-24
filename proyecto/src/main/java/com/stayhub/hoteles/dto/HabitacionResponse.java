package com.stayhub.hoteles.dto;
import java.util.Set;
public record HabitacionResponse(Long id, String numero, Integer piso, boolean activa,
                                 Long tipoHabitacionId, String tipoHabitacionCodigo,
                                 int capacidadMaxima, Set<String> caracteristicas) { }
