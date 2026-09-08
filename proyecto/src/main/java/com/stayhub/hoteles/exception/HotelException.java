package com.stayhub.hoteles.exception;
public class HotelException extends RuntimeException {
    private final CodigoErrorHotel codigo;
    public HotelException(CodigoErrorHotel codigo, String mensaje) { super(mensaje); this.codigo = codigo; }
    public CodigoErrorHotel getCodigo() { return codigo; }
}
