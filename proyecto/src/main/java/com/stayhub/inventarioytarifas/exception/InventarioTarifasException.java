package com.stayhub.inventarioytarifas.exception;

@jakarta.ejb.ApplicationException(rollback = true)
public class InventarioTarifasException extends RuntimeException {
    private final CodigoErrorInventarioTarifas codigo;

    public InventarioTarifasException(CodigoErrorInventarioTarifas codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    public InventarioTarifasException(CodigoErrorInventarioTarifas codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.codigo = codigo;
    }

    public CodigoErrorInventarioTarifas getCodigo() { return codigo; }
}
