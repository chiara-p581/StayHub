package com.stayhub.hoteles.exception;

import jakarta.ejb.ApplicationException;

/** Error de negocio esperado; el contenedor revierte la operación sin tratarlo como una falla EJB. */
@ApplicationException(rollback = true)
public class HotelException extends RuntimeException {
    private final CodigoErrorHotel codigo;
    public HotelException(CodigoErrorHotel codigo, String mensaje) { super(mensaje); this.codigo = codigo; }
    public CodigoErrorHotel getCodigo() { return codigo; }
}
