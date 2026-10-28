# StayHub

Plataforma de reservas de hotel desarrollada como Trabajo Práctico de **Desarrollo de Aplicaciones II** (UADE). Implementa una arquitectura basada en componentes distribuidos (EJB) sobre Jakarta EE, con integración a canales externos (OTAs y un PMS legado).

## Componentes y responsables

| Componente | Responsabilidad |
| --- | --- |
| `ServicioDeHoteles` | Alta, baja, modificación y consulta de hoteles, tipos de habitación y habitaciones |
| `ServicioDeInventarioYTarifas` | Disponibilidad, holds de reserva y tarifas por hotel/tipo/período |
| `ServicioDeReservas` | Ciclo de vida de una reserva (crear, confirmar, modificar, cancelar) |
| `ServicioDeCanalesExternos` | Integración con OTAs (Booking, Expedia, Airbnb, Despegar) vía REST y con el PMS legado vía SOAP |
| `ServicioDePagos` | Procesamiento de pagos asociados a una reserva |
| `ServicioDeOverbooking` | Resolución asincrónica de conflictos de sobreventa |
| `ServicioDeNotificaciones` | Envío de notificaciones (email/SMS/push) |
| `ServicioDeUsuarios` | Gestión de usuarios con dos roles: **Huésped** y **Administrador** |
| `IntegracionPms` (fachada SOAP) | Servicio web SOAP que StayHub expone al PMS legado del hotel (reservas, llegadas, disponibilidad y tarifas) |

Son 7 componentes de EJB (mínimo exigido por la cátedra: 6) más el componente de usuarios.

## Stack tecnológico

- Java 17 y Jakarta EE 10
- EJB (`@Stateless`) para la capa de negocio y transacciones administradas por el contenedor
- JAX-RS para la API REST y JAX-WS/SOAP para el cliente del PMS legado
- CDI para inyección de dependencias
- JPA/Hibernate, unidad de persistencia `StayHubPU`
- JMS para la sincronización asincrónica de Canales Externos (evita bloquear el flujo mientras se sincroniza con OTAs/PMS)
- PostgreSQL como base de datos
- WildFly como servidor de aplicaciones
- Maven para build y empaquetado WAR

## Flujo de trabajo con Git

Rama por feature → merge a `development` → merge a `main`. Los Pull Requests y los commits descriptivos son tenidos en cuenta en la evaluación.

## Cómo levantar el entorno local

### Opción recomendada: entorno completo con un comando

Esta opción levanta en contenedores la aplicación, PostgreSQL, el broker JMS
incluido en WildFly, el mock REST de OTAs/pagos y el mock SOAP del PMS. También
crea el esquema, usuarios locales y el catálogo mínimo de demostración.

**Requisito:** Docker Desktop iniciado, con Docker Compose disponible. No hace
falta instalar PostgreSQL, Maven ni WildFly en la computadora.

Desde la raíz del repositorio, en Windows PowerShell o CMD:

```bat
start-local.cmd
```

El iniciador `.cmd` funciona aunque PowerShell tenga deshabilitada la ejecución
de scripts. Como alternativa se conserva `start-local.ps1`.

En Linux o macOS:

```sh
sh ./start-local.sh
```

El primer inicio descarga y construye las imágenes, por lo que puede tardar
varios minutos. Los siguientes inicios reutilizan la caché.

| Recurso | Dirección |
| --- | --- |
| Aplicación | `https://localhost:8443/StayHub/index.html` |
| API | `https://localhost:8443/StayHub/api` |
| SOAP expuesto por StayHub | `https://localhost:8443/StayHub/soap/integracion-pms?wsdl` |
| Mock SOAP del PMS | `http://localhost:9091/PmsLegacyService?wsdl` |
| Salud del mock REST | `http://localhost:9080/health` |

Usuarios exclusivos del entorno local:

| Rol | Email | Contraseña |
| --- | --- | --- |
| Administrador | `admin@stayhub.local` | `StayHubDemo2026!` |
| Huésped | `huesped@stayhub.local` | `StayHubDemo2026!` |

El certificado HTTPS es autofirmado y exclusivo del contenedor local. Durante
el primer `start-local.cmd`, el asistente pregunta si se desea confiar en ese
certificado para el usuario actual de Windows. Los siguientes arranques
reconocen el mismo certificado y no vuelven a pedirlo.

Firefox no permite crear una excepción cuando el sitio usa HSTS. Si se omitió
el paso durante el inicio, se puede ejecutar luego:

```bat
trust-local-certificate.cmd
```

El asistente pide confirmación antes de incorporarlo al almacén de confianza
del usuario actual. No requiere permisos de Administrador. Después hay que
cerrar por completo y volver a abrir Firefox. No se debe usar este certificado
ni estas credenciales en un ambiente real.

Para revisar el estado:

```powershell
docker compose ps
docker compose logs app
```

Para detenerlo sin borrar la base local:

```bat
stop-local.cmd
```

Para reiniciar completamente los datos de prueba:

```bat
docker compose down --volumes
start-local.cmd
```

### Opción alternativa: instalación manual

Para `feature/seguridad-a`, consultar primero
[configuración local de seguridad](docs/CONFIGURACION_LOCAL_SEGURIDAD.md):
HTTPS usa el puerto 8080 y SOAP requiere permisos explícitos. Las URLs HTTP
de las instrucciones originales siguientes corresponden al entorno anterior.

Abrir **https://localhost:8080/StayHub/index.html**. HTTP queda en 8081 y
redirige a HTTPS para navegación. El modo HTTP local queda desactivado.
Los puertos se guardan en WildFly, fuera de este repositorio: cada persona debe
configurar su servidor. El script `config/configurar-seguridad-local.py` prepara
TLS y estos puertos; requiere también PostgreSQL y el datasource configurados.
Si ya tiene la configuración TLS `StayHubTLS`, puede aplicar solo los puertos:

```sh
"$WILDFLY_HOME/bin/jboss-cli.sh" --connect --file=config/https-8080.cli
"$WILDFLY_HOME/bin/jboss-cli.sh" --connect --command=:reload
```

Ejecutar desde la raíz de StayHub con `WILDFLY_HOME` apuntando a la instalación.
Los certificados privados y la confianza del certificado son locales a cada
computadora; no se comparten por Git.

### 1. Arrancar WildFly con el perfil `full`

El proyecto usa JMS para la sincronización asincrónica con OTAs/PMS, por lo que **hace falta el perfil `standalone-full`**, no el `standalone` por defecto:

```bash
.\standalone.bat -c standalone-full.xml
```

> ⚠️ Cambiar de perfil equivale a un servidor nuevo: se pierden los deployments existentes. Hay que volver a agregar el deployment de la aplicación y del datasource de PostgreSQL, y reconfigurar la URL de la base desde la consola de administración o la CLI de WildFly.

### 2. Datasource de PostgreSQL

| Propiedad | Valor |
| --- | --- |
| Pool Name | `StayHubDS` |
| JNDI Name | `java:/PostgresDS` |
| Driver | PostgreSQL |
| Unidad de persistencia (JPA) | `StayHubPU` |

El nombre administrativo del pool puede ser otro; lo que debe coincidir sí o sí es el JNDI name (`java:/PostgresDS`), que es el que referencia el código y `persistence.xml`.

### 3. Propiedades para probar Canales Externos (sincronización OTA/PMS)

Para ejercitar `/canales-externos/otas/.../sincronizaciones` y `/canales-externos/pms/sincronizaciones` hay que definir estas system properties (por CLI de WildFly):

```
/system-property=stayhub.ota.booking.url:add(value="https://c045a53c-5350-4944-87b6-b65b970cd586.mock.pstmn.io")
/system-property=stayhub.pms.wsdl:add(value="http://localhost:9091/PmsLegacyService?wsdl")
:reload
```

La URL del OTA es un mock de Postman; la del PMS asume un servicio SOAP corriendo localmente en el puerto 9091.

### 4. PMS legado simulado (SOAP)

El PMS del hotel no existe, así que se simula con un servicio JAX-WS standalone en el puerto 9091:

```bash
cd pms-legado-mock
mvn compile exec:java
```

Ver [`pms-legado-mock/README.md`](pms-legado-mock/README.md).

### 5. Base URL de la API

```
http://localhost:8080/StayHub/api
```

### 6. Servicio SOAP para el PMS legado

```
http://localhost:8080/StayHub/soap/integracion-pms?wsdl
```

Ver [`proyecto/src/main/java/com/stayhub/integracionpms/README.md`](proyecto/src/main/java/com/stayhub/integracionpms/README.md) y [`docs/entrega-4/integracion-soap-pms.md`](docs/entrega-4/integracion-soap-pms.md).

## Postman

Las colecciones para probar cada componente están en [`postman/`](postman/), junto con las instrucciones de configuración necesarias para ejecutarlas.

## Documentación técnica

En [`docs/`](docs/) se encuentra la documentación de arquitectura por componente.

## Enlaces y recursos del proyecto

- [Documentación del proyecto](https://docs.google.com/document/d/1OHKajV9d0QYh7teHyW9VJay1nTK1IWuUwMmoXS4Tee4/edit?usp=sharing)
- [Diagrama de arquitectura y de secuencia (draw.io)](https://app.diagrams.net/#G1Y206m_Rv1K3_qSDoBOPVlus0hMbEFMl_#%7B%22pageId%22%3A%22LHUSm1YBXf8T-E9KNyiL%22%7D)
- [Bocetos de frontend (Stitch)](https://stitch.withgoogle.com/projects/17811897327251791753)
- [Tablero de Jira](https://agostinabernard.atlassian.net/jira/software/projects/KAN/boards/2?filter=&groupBy=none)
