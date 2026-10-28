package com.stayhub.pmslegado;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Healthcheck sin dependencias externas para los contenedores de mocks. */
public final class Healthcheck {
    private Healthcheck() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.exit(2);
        }
        var cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        var solicitud = HttpRequest.newBuilder(URI.create(args[0])).timeout(Duration.ofSeconds(2)).GET().build();
        int estado = cliente.send(solicitud, HttpResponse.BodyHandlers.discarding()).statusCode();
        if (estado < 200 || estado >= 300) {
            System.exit(1);
        }
    }
}
