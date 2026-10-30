# Matriz ejecutable de seguridad

Esta matriz reúne la evidencia automatizada de KAN-100. Las pruebas REST y de navegador se ejecutan con Playwright contra una instancia desplegada; las verificaciones que requieren un certificado PMS se ejecutan en la suite Java con un certificado controlado.

| Riesgo | Caso automatizado | Resultado esperado |
|---|---|---|
| BOLA / IDOR | `live-security-matrix.spec.cjs`: usuario A consulta el perfil de B | 403 o 404 sin datos de B |
| BFLA | huésped intenta crear un hotel | 403 |
| Mass assignment | registro envía `rol=ADMIN` | la cuenta queda `HUESPED` y no expone hash |
| SQL injection | identificador `1 OR 1=1` | 400/404, nunca se interpreta como consulta |
| NoSQL injection | No aplica: StayHub no usa una base NoSQL | cubierto por delimitación de alcance |
| XSS | payload con `script` y `onerror` pasa por `StayHubDOM` | no queda código ejecutable |
| XXE | `SeguridadHttpFilterTest.soapAutenticadoConDtdSeRechazaAntesDeLlegarAlEndpoint` | 400 y el endpoint no se invoca |
| Fuerza bruta | 61 intentos de login inválidos | 429 y `Retry-After: 900` |
| Propiedad de reservas y pagos | pruebas de servicios `ServicioDeReservasImplTest` y `ServicioDePagosSeguridadTest` | la identidad de sesión prevalece sobre el cliente |

## Ejecución

Con StayHub desplegado en WildFly:

```powershell
cd e2e
$env:STAYHUB_BASE_URL = "https://localhost:8443/StayHub"
npx playwright test tests/live-security-matrix.spec.cjs
```

Suite Java, incluida la prueba XXE autenticada:

```powershell
cd proyecto
mvn test
```

El limitador de fuerza bruta mantiene estado en memoria durante quince minutos. En un entorno compartido conviene ejecutar esta especificación de forma aislada o reiniciar la aplicación antes de repetirla.
