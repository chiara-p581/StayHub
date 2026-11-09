# Prueba de carga KAN-98

La prueba simula **50 usuarios concurrentes durante cinco minutos** y exige, globalmente y para cada endpoint, un p95 menor a 500 ms y una tasa de error HTTP menor al 1%.

## Endpoints elegidos

1. `GET /hoteles`: es la entrada al catálogo y al embudo de reserva.
2. `GET /inventario-tarifas/disponibilidad`: determina si una reserva puede realizarse sin sobreventa.
3. `GET /inventario-tarifas/tarifas`: entrega el precio que se muestra y utiliza en el flujo de reserva.

Son operaciones públicas, de solo lectura y representativas del recorrido más frecuente. Al no crear datos, la carga es repetible y no contamina inventario, reservas ni pagos.

## Ejecución

Primero se debe levantar StayHub y cargar los datos demo:

Con WildFly local ya iniciado y el WAR desplegado, ejecutar directamente:

```sh
BASE_URL=https://localhost:8080/StayHub/api sh ./performance/run.sh
```

La alternativa Docker para un entorno sin WildFly es:

```sh
sh ./start-local.sh
```

Luego:

```sh
sh ./performance/run.sh
```

El script usa una instalación local de k6 si existe; de lo contrario utiliza la imagen Docker fijada en `grafana/k6:2.3.0`. Para una verificación corta del guion se pueden sobrescribir los parámetros:

```sh
VUS=1 DURATION=10s sh ./performance/run.sh
```

Los resultados se escriben en:

- `performance/reports/k6-summary.md`: informe legible para adjuntar a la ejecución.
- `performance/reports/k6-summary.json`: métricas completas de k6.

La integración continua ejecuta la prueba completa, publica ambos archivos como artefacto `load-test-evidence` e incorpora el informe Markdown al resumen de la ejecución.
