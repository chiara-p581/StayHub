package com.stayhub.hoteles.dto;
import java.util.Set;
public record HotelRequest(String nombre, String direccion, String ciudad, String pais,
                           String descripcion, Set<String> servicios) { }
