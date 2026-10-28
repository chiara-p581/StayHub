package com.stayhub.pmslegado;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** Mock REST determinista para las OTAs y la pasarela de pagos del entorno local. */
public final class OtaMock {
    private static final int MAX_BODY = 1_048_576;

    private OtaMock() {
    }

    public static void main(String[] args) throws IOException {
        int puerto = Integer.getInteger("ota.puerto", 9080);
        HttpServer servidor = HttpServer.create(new InetSocketAddress("0.0.0.0", puerto), 0);
        servidor.createContext("/health", intercambio -> responder(intercambio, 200, "{\"estado\":\"ok\"}"));
        servidor.createContext("/", OtaMock::procesar);
        servidor.setExecutor(Executors.newCachedThreadPool());
        servidor.start();
        System.out.println("Mock REST de OTAs escuchando en http://0.0.0.0:" + puerto);
    }

    private static void procesar(HttpExchange intercambio) throws IOException {
        String metodo = intercambio.getRequestMethod();
        if (!"PUT".equals(metodo) && !"POST".equals(metodo)) {
            responder(intercambio, 405, "{\"error\":\"metodo_no_permitido\"}");
            return;
        }
        byte[] cuerpo = intercambio.getRequestBody().readNBytes(MAX_BODY + 1);
        if (cuerpo.length > MAX_BODY) {
            responder(intercambio, 413, "{\"error\":\"cuerpo_demasiado_grande\"}");
            return;
        }
        String respuesta = "{\"exitoso\":true,\"ruta\":\""
                + intercambio.getRequestURI().getPath().replace("\"", "") + "\",\"bytes\":" + cuerpo.length + "}";
        responder(intercambio, 200, respuesta);
    }

    private static void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        intercambio.sendResponseHeaders(estado, bytes.length);
        try (var salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }
}
