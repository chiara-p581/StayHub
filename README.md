# StayHub

Plataforma de reservas de hotel desarrollada como Trabajo Práctico de **Desarrollo de Aplicaciones II** (UADE). Implementa una arquitectura basada en componentes distribuidos (EJB) sobre Jakarta EE, con integración a canales externos (OTAs y un PMS legado).

## Componentes y responsables

| Componente | Responsabilidad | Responsable |
| --- | --- | --- |
| `ServicioDeHoteles` | Alta, baja, modificación y consulta de hoteles, tipos de habitación y habitaciones | Agostina |
| `ServicioDeInventarioYTarifas` | Disponibilidad, holds de reserva y tarifas por hotel/tipo/período | Sabrina |
| `ServicioDeReservas` | Ciclo de vida de una reserva (crear, confirmar, modificar, cancelar) | Magui |
| `ServicioDeCanalesExternos` | Integración con OTAs (Booking, Expedia, Airbnb, Despegar) vía REST y con el PMS legado vía SOAP | Chiara |
| `ServicioDePagos` | Procesamiento de pagos asociados a una reserva | Matilda |
| `ServicioDeOverbooking` | Resolución asincrónica de conflictos de sobreventa | Delfina |
| `ServicioDeNotificaciones` | Envío de notificaciones (email/SMS/push) | Delfina |
| `ServicioDeUsuarios` | Gestión de usuarios con dos roles: **Huésped** y **Administrador** | Matilda |

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

### 4. Base URL de la API

```
http://localhost:8080/StayHub/api
```

## Postman

Las colecciones para probar cada componente están en [`postman/`](postman/), junto con las instrucciones de configuración necesarias para ejecutarlas.

## Documentación técnica

En [`docs/`](docs/) se encuentra la documentación de arquitectura por componente (entregas parciales) y las auditorías técnicas realizadas sobre el proyecto (ver [`docs/auditorias/`](docs/auditorias/)).

## Enlaces y recursos del proyecto

- [Consigna del TP](https://docs.google.com/document/d/1ayBCj8IYczsX330MOjszmPCZdyv3srn9vVeUWR9JhRo/edit?usp=sharing)
- [Documento de definición de componentes](https://docs.google.com/document/d/1OHKajV9d0QYh7teHyW9VJay1nTK1IWuUwMmoXS4Tee4/edit?usp=sharing)
- [Documento entregado — Entrega Parcial N.º 1](https://docs.google.com/document/d/1hc6s02mDkNvDzavMGNb4RZ-cR2EbvUFKLdLUXj0c6zs/edit?usp=sharing)
- [Diagrama de arquitectura y de secuencia (draw.io)](https://app.diagrams.net/#G1Y206m_Rv1K3_qSDoBOPVlus0hMbEFMl_#%7B%22pageId%22%3A%22LHUSm1YBXf8T-E9KNyiL%22%7D)
- [Bocetos de frontend (Stitch)](https://stitch.withgoogle.com/projects/17811897327251791753)
- [Tablero de Jira](https://agostinabernard.atlassian.net/)
