package com.stayhub.hoteles.dto;
import java.util.Set;
public record TipoHabitacionResponse(Long id, String codigo, String nombre, String descripcion,
                                     int capacidadMaxima, boolean activo, Set<String> caracteristicas) { }
