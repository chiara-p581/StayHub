package com.stayhub.inventarioytarifas.model;

import com.stayhub.inventarioytarifas.exception.CodigoErrorInventarioTarifas;
import com.stayhub.inventarioytarifas.exception.InventarioTarifasException;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Capacidad y tarifa de un tipo de habitación de un hotel para un día
 * puntual. Es la unidad mínima de inventario: la disponibilidad y las
 * tarifas de un rango de fechas (lo que consultan ServicioDeCanalesExternos
 * y ServicioDeOverbooking, y lo que retiene ServicioDeReservas con un hold)
 * se calculan agregando una fila de estas por cada noche del rango.
 */
@Entity
@Table(
    name = "inventario_diario",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_hotel_tipo_fecha",
        columnNames = {"hotel_id", "tipo_habitacion", "fecha"}
    )
)
public class InventarioDiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    @Column(name = "tipo_habitacion", nullable = false, length = 30)
    private String tipoHabitacion;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "unidades_totales", nullable = false)
    private int unidadesTotales;

    @Column(name = "unidades_ocupadas", nullable = false)
    private int unidadesOcupadas;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false, length = 3)
    private String moneda;

    /**
     * Red de seguridad ante ocupaciones concurrentes. La protección principal
     * es el bloqueo pesimista que toma ServicioDeInventarioYTarifasImpl antes
     * de leer el cupo (ver InventarioDiarioRepository.buscarPorHotelTipoYFechaBloqueando):
     * con ese lock, dos transacciones que compiten por la misma fila se
     * serializan y la segunda ve el cupo ya descontado. @Version queda igual
     * para los caminos que no pasan por ese bloqueo.
     */
    @Version
    private Long version;

    protected InventarioDiario() {
        // requerido por JPA
    }

    public InventarioDiario(Long hotelId, String tipoHabitacion, LocalDate fecha,
                             int unidadesTotales, BigDecimal precio, String moneda) {
        this.hotelId = hotelId;
        this.tipoHabitacion = tipoHabitacion;
        this.fecha = fecha;
        this.unidadesTotales = unidadesTotales;
        this.unidadesOcupadas = 0;
        this.precio = precio;
        this.moneda = moneda;
    }

    public void actualizarCapacidadYTarifa(int unidadesTotales, BigDecimal precio, String moneda) {
        this.unidadesTotales = unidadesTotales;
        this.precio = precio;
        this.moneda = moneda;
    }

    /**
     * Descuenta cupo. Invariante del dominio: unidadesOcupadas nunca puede
     * superar unidadesTotales. Quien llama ya debería haber verificado la
     * disponibilidad (y, en el flujo de holds, bajo bloqueo pesimista); esta
     * validación es el último guardarraíl para que una sobreventa falle acá
     * en vez de quedar persistida.
     */
    public void ocupar(int cantidad) {
        if (cantidad > getUnidadesDisponibles()) {
            throw new InventarioTarifasException(CodigoErrorInventarioTarifas.SOBREVENTA_DETECTADA,
                    "No se puede ocupar " + cantidad + " unidad/es de hotelId=" + hotelId
                            + ", tipoHabitacion=" + tipoHabitacion + " el " + fecha
                            + ": solo hay " + getUnidadesDisponibles() + " disponible/s");
        }
        this.unidadesOcupadas += cantidad;
    }

    public void liberar(int cantidad) {
        this.unidadesOcupadas = Math.max(0, this.unidadesOcupadas - cantidad);
    }

    public int getUnidadesDisponibles() { return unidadesTotales - unidadesOcupadas; }

    public Long getId() { return id; }
    public Long getHotelId() { return hotelId; }
    public String getTipoHabitacion() { return tipoHabitacion; }
    public LocalDate getFecha() { return fecha; }
    public int getUnidadesTotales() { return unidadesTotales; }
    public int getUnidadesOcupadas() { return unidadesOcupadas; }
    public BigDecimal getPrecio() { return precio; }
    public String getMoneda() { return moneda; }
}
