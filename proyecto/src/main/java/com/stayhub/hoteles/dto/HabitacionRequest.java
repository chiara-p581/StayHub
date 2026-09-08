package com.stayhub.hoteles.dto;
import java.util.Set;
public record HabitacionRequest(Long tipoHabitacionId, String numero, Integer piso,
                                Set<String> caracteristicas) { }
