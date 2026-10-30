package com.stayhub.reservas.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EstadoReservaTransicionesTest {

    private static final Map<EstadoReserva, Set<EstadoReserva>> PERMITIDAS = Map.of(
            EstadoReserva.PENDIENTE, EnumSet.of(EstadoReserva.CONFIRMADA, EstadoReserva.MODIFICADA,
                    EstadoReserva.CANCELADA, EstadoReserva.RECHAZADA),
            EstadoReserva.CONFIRMADA, EnumSet.of(EstadoReserva.MODIFICADA, EstadoReserva.CANCELADA),
            EstadoReserva.MODIFICADA, EnumSet.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA,
                    EstadoReserva.CANCELADA, EstadoReserva.RECHAZADA),
            EstadoReserva.CANCELADA, EnumSet.noneOf(EstadoReserva.class),
            EstadoReserva.RECHAZADA, EnumSet.of(EstadoReserva.MODIFICADA, EstadoReserva.CANCELADA));

    static Stream<Arguments> todasLasCombinaciones() {
        return Stream.of(EstadoReserva.values())
                .flatMap(origen -> Stream.of(EstadoReserva.values())
                        .map(destino -> Arguments.of(origen, destino,
                                origen != destino && PERMITIDAS.get(origen).contains(destino))));
    }

    @ParameterizedTest(name = "{0} -> {1}: permitida={2}")
    @MethodSource("todasLasCombinaciones")
    void laTablaDefineTodasLasTransiciones(EstadoReserva origen, EstadoReserva destino, boolean permitida) {
        assertEquals(permitida, origen.permiteTransicionA(destino));
    }
}
