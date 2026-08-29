package com.stayhub.hoteles.dto;
import java.util.List;
import java.util.Set;
public record HotelResponse(Long id, String nombre, String direccion, String ciudad, String pais,
                            String descripcion, boolean activo, Set<String> servicios,
                            List<TipoHabitacionResponse> tiposHabitacion,
                            List<HabitacionResponse> habitaciones) { }
