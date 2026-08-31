package com.stayhub.inventarioytarifas.exception;

public enum CodigoErrorInventarioTarifas {
    SOLICITUD_INVALIDA,
    HOLD_NO_ENCONTRADO,
    TRANSICION_DE_ESTADO_INVALIDA,
    /**
     * Último guardarraíl del dominio: se intentó ocupar más unidades de las
     * disponibles en una fila de InventarioDiario. Con el bloqueo pesimista de
     * ServicioDeInventarioYTarifasImpl no debería ocurrir nunca desde los
     * flujos propios; si aparece, indica que alguien mutó el inventario por
     * fuera del servicio (o un bug), no un pedido inválido del cliente.
     */
    SOBREVENTA_DETECTADA
}
