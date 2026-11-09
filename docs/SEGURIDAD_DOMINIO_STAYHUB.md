# Dominio de seguridad exclusivo de StayHub

## Decisión

`StayHub.war` selecciona `StayHubSecurity` en `WEB-INF/jboss-web.xml`.
En Undertow ese nombre se asocia a Elytron `ApplicationDomain`, con
`enable-jaspi=true` e `integrated-jaspi=false`. El mecanismo de Jakarta Security
de StayHub comunica al contenedor la identidad y los roles obtenidos de la
sesión autenticada por la aplicación. No requiere duplicar sus usuarios en
`ApplicationRealm`.

El alcance es el despliegue que referencia ese nombre. El dominio compartido
`other` conserva `integrated-jaspi=true`; tampoco se cambia el dominio por
defecto de EJB. Se registra una asociación EJB llamada `StayHubSecurity` para
su selección explícita. El nombre es exclusivo por convención de despliegue:
otro administrador podría vincular otro WAR y debe evitar hacerlo.

Esto no concede permisos a partir de headers o roles enviados por el cliente.
La autenticación y los roles dependen del mecanismo de sesión de StayHub.
La autorización HTTP y las comprobaciones de propiedad de los servicios siguen
siendo necesarias, además de las anotaciones declarativas del contenedor.

## Aplicación y comprobación

`config/seguridad-declarativa.cli` crea o actualiza las asociaciones y recarga
WildFly. `config/configurar-seguridad-local.py` aplica esas operaciones sin
recargar automáticamente. Docker usa las mismas asociaciones en
`docker/wildfly/configure.cli` como alternativa de despliegue.

Verificar con la CLI de administración:

```text
/subsystem=undertow/application-security-domain=StayHubSecurity:read-resource
/subsystem=undertow/application-security-domain=other:read-resource
/subsystem=ejb3/application-security-domain=StayHubSecurity:read-resource
/deployment=StayHub.war:read-attribute(name=status)
```

En WildFly 41 local se verificaron `StayHubSecurity.integrated-jaspi=false`,
`other.integrated-jaspi=true` y despliegue `OK`. Se verificó compra con pago,
restricción de ADMIN, rechazo de acceso a otro usuario y rechazo de fuerza bruta.

La configuración de WildFly se respaldó en la carpeta privada
`.local-security/backups/20261108-before-stayhub-security/`; no debe publicarse,
porque puede contener credenciales. Para revertir, coordinar el descriptor y
la configuración del servidor, restaurar el respaldo y redesplegar un WAR
compatible. Eliminar solamente el dominio dejando el descriptor lo rompe.

## Alcance de la evidencia

Las pruebas funcionales y de abuso aportan evidencia de los casos cubiertos;
no equivalen a una auditoría completa ni a una garantía de ausencia de fallas.
La configuración y sus respaldos deben tratarse como material privado.
