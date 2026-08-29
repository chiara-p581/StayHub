package com.stayhub.hoteles.dto;
import java.util.Set;
public record TipoHabitacionRequest(String codigo, String nombre, String descripcion,
                                    int capacidadMaxima, Set<String> caracteristicas) { }
