# OWASP ZAP autenticado

Esta carpeta contiene la automatización de KAN-99. El flujo de CI levanta un
entorno descartable con PostgreSQL, WildFly y los mocks, carga el catálogo demo
y ejecuta OWASP ZAP como dos identidades:

- huésped: `huesped@stayhub.local`;
- administrador: `admin@stayhub.local`, completando el MFA TOTP generado por el
  propio entorno.

El script `run-authenticated-scan.sh` genera reportes JSON, HTML y Markdown en
`ci-evidence/zap`. El job falla cuando aparece una alerta High o una instancia
Medium que no esté corregida ni explícitamente revisada en
`accepted-medium-alerts.json`. La evidencia `medium-review.json` relaciona cada
instancia Medium con su justificación y `unreviewed-medium.json` debe quedar
vacío.

Una regla aceptada debe identificar el `pluginId`, limitar las URLs mediante
una expresión regular y explicar el riesgo residual o por qué la respuesta está
fuera del contexto `/StayHub`. No se deben crear reglas genéricas que oculten
alertas nuevas.

## Ejecución segura

```bash
docker compose up --detach --build --wait --wait-timeout 600
docker compose exec -T app /opt/jboss/load-demo.sh
bash security/zap/run-authenticated-scan.sh
docker compose down --volumes --remove-orphans
```

No debe ejecutarse el escaneo activo contra Aiven ni contra otro entorno con
datos compartidos. La ejecución local sobre Aiven se limita al rastreo pasivo y
a solicitudes autenticadas de lectura.
