package com.stayhub.hoteles.dto;
import java.math.BigDecimal;
import java.util.Set;
public record TipoHabitacionRequest(String codigo, String nombre, String descripcion,
                                    BigDecimal capacidadMaxima, Set<String> caracteristicas) { }
