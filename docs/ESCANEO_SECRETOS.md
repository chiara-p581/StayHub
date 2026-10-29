# Escaneo de secretos en el historial Git (G6)

Evidencia para la condición eliminatoria **G6** de los Criterios de Testing (Clase 15): sin contraseñas, tokens, claves ni certificados en ningún commit del historial.

## Control implementado

| Elemento | Detalle |
|---|---|
| Herramienta | gitleaks 8.30.1 (reglas por defecto + [.gitleaks.toml](../.gitleaks.toml)) |
| Job de CI | `secrets-scan` en [.github/workflows/security.yml](../.github/workflows/security.yml) |
| Alcance | Todos los commits de todas las ramas (`git --log-opts="--all"`, checkout con `fetch-depth: 0`) y el árbol de trabajo (`dir`) |
| Disparadores | push a `development`/`main`, PR hacia esas ramas, tags `v*-testing`, manual y semanal |
| Si hay hallazgos | gitleaks sale con código distinto de 0 y el pipeline falla |
| Evidencia | Artefacto `secrets-evidence` (`gitleaks-report.json` y `gitleaks-tree-report.json`, 14 días). Los valores salen redactados (`--redact`) |

## Ejecución local

Con el binario de gitleaks (https://github.com/gitleaks/gitleaks/releases):

```
gitleaks git --config .gitleaks.toml --log-opts="--all" --redact -v .
gitleaks dir --config .gitleaks.toml --redact -v .
```

## Resultado de la revisión (2026-10-28)

Historial de todas las ramas: 102 commits con diff escaneados (última revisión) (los merges sin cambios propios no tienen contenido que escanear).

| Escaneo | Resultado |
|---|---|
| gitleaks sobre historial, sin config propia | 3 hallazgos, todos falsos positivos (ver abajo) |
| gitleaks sobre historial, con `.gitleaks.toml` | 0 hallazgos |
| gitleaks sobre el árbol actual | 0 hallazgos |
| Archivos `.pem/.p12/.jks/.key/.crt/.env/mail.properties` en cualquier commit | Ninguno |
| Archivos trackeados hoy bajo `.local-security/` o `.local-demo/` | Ninguno |
| Contenido de los `StayHub.war` commiteados en el pasado (`proyecto/target/`) | Sin credenciales ni claves (`persistence.xml` sin usuario ni password) |
| Control de sanidad: secretos plantados en un repo de prueba | Detectados |

### Hallazgo 1: clave HMAC de fixture (falso positivo)

- Regla `generic-api-key`, [AutenticacionWebhookOtaTest.java:18](../proyecto/src/test/java/com/stayhub/seguridad/AutenticacionWebhookOtaTest.java#L18), commit `eecd104`.
- El valor `0123456789abcdef...` es una secuencia inventada que solo firma mensajes dentro del test. No da acceso a ningún sistema.
- **Revocación:** no aplica, no hay nada que rotar.
- **Eliminación del historial:** no se reescribió. Sería destructivo (cambia todos los hashes de commit, obliga a un force-push y rompe el trabajo del resto del equipo) para eliminar un valor que no es secreto.
- **Tratamiento:** excepción acotada a ese archivo y ese valor en `.gitleaks.toml`. Cualquier otro hallazgo sigue haciendo fallar el pipeline.

### Hallazgo 2: vector de prueba RFC 6238 (falso positivo)

- Regla `generic-api-key`, [MfaTotpTest.java:12](../proyecto/src/test/java/com/stayhub/seguridad/MfaTotpTest.java#L12), commit `c0f4b08` (rama `feature/seguridad-a`). Lo detectó el primer run del job en GitHub Actions, con 99 commits.
- `GEZDGNBVGY3TQOJQ...` es el Base32 de `12345678901234567890`, el secreto de los vectores de prueba publicados en el RFC 6238. El test lo usa para comparar contra códigos TOTP conocidos. No protege ninguna cuenta.
- **Revocación y eliminación del historial:** no aplican, por las mismas razones que el hallazgo 1.
- **Tratamiento:** segunda excepción acotada a ese archivo y ese valor en `.gitleaks.toml`.

### Hallazgo 3: contraseña débil usada como entrada inválida (falso positivo)

- Regla `generic-api-key`, [ServicioDeUsuariosCoberturaTest.java:499](../proyecto/src/test/java/com/stayhub/usuarios/service/ServicioDeUsuariosCoberturaTest.java#L499), commit `dc40a40` (tests de cobertura JaCoCo). Lo detectó el job en GitHub Actions, con 102 commits.
- `qwertyuiop12` es una contraseña débil que el test le pasa a `resetearPassword` para comprobar que el servicio la rechaza. No corresponde a ninguna cuenta.
- **Revocación y eliminación del historial:** no aplican, por las mismas razones que el hallazgo 1.
- **Tratamiento:** tercera excepción acotada a ese archivo y ese valor en `.gitleaks.toml`.

Criterio para futuras excepciones: solo valores públicos o inventados que viven en tests, siempre acotados por archivo y valor, nunca por regla completa.

## Credenciales de demostración presentes en el repo (decisión consciente)

Gitleaks no las marca, pero un revisor las puede ver. Existen para que `docker compose up` funcione desde cero con un solo comando (G1) y **no deben usarse fuera del entorno local de demo**.

| Dónde | Qué | Riesgo |
|---|---|---|
| [compose.yaml](../compose.yaml), [docker/wildfly/configure.cli](../docker/wildfly/configure.cli) | Contraseña de PostgreSQL local | La base solo se expone dentro de la red de Compose |
| [docker/wildfly/configure.cli](../docker/wildfly/configure.cli) | Secretos de webhook `demo-<canal>-secret-change-me` | Solo válidos contra el entorno de demo |
| README, scripts `start-local.*`, [load-demo.sh](../docker/wildfly/load-demo.sh) | Usuario `admin@stayhub.local` con contraseña de demo (en la base hay solo el hash PBKDF2) | Cuenta ADMIN de demo |
| [postman/Stayhub usuarios.postman.collection.json](../postman/Stayhub%20usuarios.postman.collection.json) | Usuarios de ejemplo con `password123` | Datos de ejemplo para pruebas manuales |

Para producción: inyectarlos desde variables de entorno o un gestor de secretos y rotarlos. Los secretos reales (correo SMTP, TLS) ya están fuera del repo: `mail.properties`, `.env*`, `.local-security/` y `.local-demo/` están en `.gitignore`, y la contraseña del keystore TLS se genera aleatoriamente en `configurar-seguridad-local.py`.

## Verificación en CI

- El primer run del job `secrets-scan` cortó ante el hallazgo 2, lo que confirmó que el control falla cuando debe.
- Con las excepciones incluidas (commit `f928a91`), el job quedó en verde: 0 hallazgos en el historial y en el árbol. Los reportes están en el artefacto `secrets-evidence` del run.
- Link al run en verde (PR #67): https://github.com/chiara-p581/StayHub/actions/runs/40797406636/job/123197119416?pr=67
- Lista de runs del workflow: https://github.com/chiara-p581/StayHub/actions/workflows/security.yml
- Antes de crear el tag `v1.0-testing`, confirmar que el run sobre `main` esté en verde (G3 y G6).

## Si aparece un secreto real
- Revocarlo primero en el proveedor, después eliminarlo del historial con `git filter-repo` coordinando con el equipo, y recién ahí actualizar este documento.
