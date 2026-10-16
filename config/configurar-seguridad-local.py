#!/usr/bin/env python3
"""Configura TLS y recursos de un WildFly local existente. No modifica la BD."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--wildfly-home', required=True, type=Path)
parser.add_argument('--openssl', default='openssl')
parser.add_argument('--hoteles-pms', default='', help='Solo IDs cuyo permiso PMS fue confirmado; vacío bloquea SOAP')
parser.add_argument('--controller', default='localhost:9990')
a = parser.parse_args()
hoteles = [int(x) for x in a.hoteles_pms.split(',') if x.strip()]
if len(hoteles) > 100 or any(x <= 0 for x in hoteles):
    parser.error('Los hoteles deben ser IDs positivos, máximo 100')
root = Path(__file__).resolve().parent.parent
privado = root / '.local-security'
privado.mkdir(mode=0o700, exist_ok=True)
os.chmod(privado, 0o700)
os.umask(0o077)
certs = privado / 'certificados'
certs.mkdir(mode=0o700, exist_ok=True)
secretos = privado / 'secretos.json'
if secretos.exists():
    password = json.loads(secretos.read_text())['tls_store_password']
else:
    password = secrets.token_urlsafe(36)
    secretos.write_text(json.dumps({'tls_store_password': password}))
os.chmod(secretos, 0o600)
env = dict(os.environ, STAYHUB_TLS_STORE_PASSWORD=password)

def ejecutar(argv):
    r = subprocess.run([str(x) for x in argv], env=env, capture_output=True, text=True)
    if r.returncode:
        # Evitar volcar argumentos, configuración o credenciales al log.
        raise RuntimeError('Falló la herramienta ' + Path(str(argv[0])).name + ': ' + r.stderr[-400:].replace(password, '[oculto]'))
    return r.stdout

def openssl(*args):
    return ejecutar([a.openssl, *args])

if not (certs / 'server.p12').exists():
    openssl('genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:3072', '-aes-256-cbc', '-pass', 'env:STAYHUB_TLS_STORE_PASSWORD', '-out', certs / 'ca-key.pem')
    openssl('req', '-new', '-x509', '-sha256', '-days', '365', '-key', certs / 'ca-key.pem', '-passin', 'env:STAYHUB_TLS_STORE_PASSWORD', '-subj', '/CN=StayHub desarrollo local CA', '-addext', 'basicConstraints=critical,CA:TRUE,pathlen:0', '-addext', 'keyUsage=critical,keyCertSign,cRLSign', '-out', certs / 'ca.pem')
    for nombre, cn, uso in [('server', 'localhost', 'serverAuth'), ('pms', 'StayHub PMS local', 'clientAuth')]:
        openssl('genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:3072', '-aes-256-cbc', '-pass', 'env:STAYHUB_TLS_STORE_PASSWORD', '-out', certs / (nombre + '-key.pem'))
        openssl('req', '-new', '-sha256', '-key', certs / (nombre + '-key.pem'), '-passin', 'env:STAYHUB_TLS_STORE_PASSWORD', '-subj', '/CN=' + cn, '-out', certs / (nombre + '.csr'))
        ext = certs / (nombre + '.ext')
        ext.write_text('basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=' + uso + '\n' + ('subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1\n' if nombre == 'server' else ''))
        openssl('x509', '-req', '-sha256', '-days', '90', '-in', certs / (nombre + '.csr'), '-CA', certs / 'ca.pem', '-CAkey', certs / 'ca-key.pem', '-passin', 'env:STAYHUB_TLS_STORE_PASSWORD', '-set_serial', str(secrets.randbits(128)), '-extfile', ext, '-out', certs / (nombre + '.pem'))
        openssl('pkcs12', '-export', '-name', nombre, '-in', certs / (nombre + '.pem'), '-inkey', certs / (nombre + '-key.pem'), '-passin', 'env:STAYHUB_TLS_STORE_PASSWORD', '-certfile', certs / 'ca.pem', '-passout', 'env:STAYHUB_TLS_STORE_PASSWORD', '-out', certs / (nombre + '.p12'))
    keytool = Path(os.environ.get('JAVA_HOME', '')) / 'bin/keytool'
    if not keytool.exists(): keytool = shutil.which('keytool')
    ejecutar([keytool, '-importcert', '-noprompt', '-alias', 'stayhub-local-ca', '-file', certs / 'ca.pem', '-keystore', certs / 'trust.p12', '-storetype', 'PKCS12', '-storepass:env', 'STAYHUB_TLS_STORE_PASSWORD'])

# El formato PEM es ASCII; la huella siempre corresponde al certificado DER.
import ssl
huella = hashlib.sha256(ssl.PEM_cert_to_DER_cert((certs / 'pms.pem').read_text())).hexdigest()
backup = privado / 'backups' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
backup.mkdir(parents=True, mode=0o700)
shutil.copy2(a.wildfly_home / 'standalone/configuration/standalone-full.xml', backup / 'standalone-full.xml')
os.chmod(backup / 'standalone-full.xml', 0o600)

# Los secretos se guardan solo en archivos privados y en el XML local de WildFly.
def q(value): return json.dumps(str(value))
def upsert(address, attrs):
    values = ','.join(k + '=' + v for k, v in attrs.items())
    cmds = [f'if (outcome != success) of {address}:read-resource', f' {address}:add({values})', 'else']
    cmds += [f' {address}:write-attribute(name={k},value={v})' for k, v in attrs.items()]
    return cmds + ['end-if']

cmds = []
cmds += upsert('/subsystem=elytron/key-store=StayHubTLSKeys', {'path': q(certs / 'server.p12'), 'type': 'PKCS12', 'credential-reference': '{clear-text=' + q(password) + '}'})
cmds += upsert('/subsystem=elytron/key-store=StayHubTLSTrust', {'path': q(certs / 'trust.p12'), 'type': 'PKCS12', 'credential-reference': '{clear-text=' + q(password) + '}'})
cmds += upsert('/subsystem=elytron/key-manager=StayHubTLSKeyManager', {'key-store': 'StayHubTLSKeys', 'alias-filter': 'server', 'credential-reference': '{clear-text=' + q(password) + '}'})
cmds += upsert('/subsystem=elytron/trust-manager=StayHubTLSTrustManager', {'key-store': 'StayHubTLSTrust'})
cmds += upsert('/subsystem=elytron/server-ssl-context=StayHubTLS', {'key-manager': 'StayHubTLSKeyManager', 'trust-manager': 'StayHubTLSTrustManager', 'protocols': '["TLSv1.3","TLSv1.2"]', 'want-client-auth': 'true', 'need-client-auth': 'false'})
cmds += ['batch', '/subsystem=undertow/server=default-server/https-listener=https:write-attribute(name=ssl-context,value=StayHubTLS)']
for tipo, listener in [('https-listener', 'https'), ('http-listener', 'default')]:
    prefix = f'/subsystem=undertow/server=default-server/{tipo}={listener}'
    attrs = {'max-post-size': 65536, 'max-header-size': 16384, 'max-headers': 100, 'max-parameters': 100, 'max-connections': 200, 'request-parse-timeout': 10000, 'read-timeout': 10000, 'write-timeout': 10000, 'http2-max-concurrent-streams': 32, 'http2-max-header-list-size': 16384, 'no-request-timeout': 15000}
    cmds += [f'{prefix}:write-attribute(name={k},value={v})' for k, v in attrs.items()]
    cmds += [f'{prefix}:write-attribute(name=proxy-address-forwarding,value=false)', f'{prefix}:write-attribute(name=allow-encoded-slash,value=false)']
cmds += ['run-batch']
cmds += ['batch',
         '/socket-binding-group=standard-sockets/socket-binding=http:write-attribute(name=port,value=8081)',
         '/socket-binding-group=standard-sockets/socket-binding=https:write-attribute(name=port,value=8080)',
         'run-batch']
cmds += upsert('/subsystem=undertow/configuration=filter/expression-filter=StayHubHTTPSRedirect', {
    'expression': q("method(GET) or method(HEAD) -> redirect('https://localhost:8080%U%q') else response-code(426)")})
cmds += upsert('/subsystem=undertow/server=default-server/host=default-host/filter-ref=StayHubHTTPSRedirect', {
    'priority': '2', 'predicate': q("path-prefix('/StayHub') and not secure()")})
cmds += upsert('/subsystem=undertow/configuration=filter/request-limit=StayHubRequestLimit', {
    'max-concurrent-requests': '32', 'queue-size': '32'})
cmds += upsert('/subsystem=undertow/server=default-server/host=default-host/filter-ref=StayHubRequestLimit', {
    'priority': '10', 'predicate': q("path-prefix('/StayHub')")})
# Ningún hotel se infiere como autorizado a partir de ofertas OTA.
# Bloqueo de borde adicional mientras no haya una vinculación PMS confirmada.
if not hoteles:
    cmds += upsert('/subsystem=undertow/configuration=filter/expression-filter=StayHubPMSBlocked', {
        'expression': q('response-code(403)')})
    cmds += upsert('/subsystem=undertow/server=default-server/host=default-host/filter-ref=StayHubPMSBlocked', {
        'priority': '1', 'predicate': q("path-prefix('/StayHub/soap')")})
else:
    cmds += ['if (outcome == success) of /subsystem=undertow/server=default-server/host=default-host/filter-ref=StayHubPMSBlocked:read-resource',
             ' /subsystem=undertow/server=default-server/host=default-host/filter-ref=StayHubPMSBlocked:remove', 'end-if']
for prop, val in [('stayhub.seguridad.http-local', 'false'), ('stayhub.pms.certificado-sha256', huella), ('stayhub.pms.hoteles', ','.join(map(str, hoteles))), ('stayhub.web.base-url', 'https://localhost:8080/StayHub')]:
    cmds += upsert('/system-property=' + prop, {'value': q(val)})
cmds += ['batch']
ds = '/subsystem=datasources/data-source=StayHubDS'
for k, v in {'max-pool-size': 20, 'min-pool-size': 0, 'blocking-timeout-wait-millis': 5000, 'query-timeout': 15, 'set-tx-query-timeout': 'true'}.items():
    cmds += [f'{ds}:write-attribute(name={k},value={v})']
cmds += ['run-batch']
for k, v in [('connectTimeout', '10'), ('socketTimeout', '30')]:
    cmds += [f'if (outcome != success) of {ds}/connection-properties={k}:read-resource', f' {ds}/connection-properties={k}:add(value={q(v)})', 'end-if']
cli = a.wildfly_home / 'bin/jboss-cli.sh'

def aplicar(comandos, etiqueta):
    path = privado / 'aplicar.cli'
    path.write_text('\n'.join(comandos) + '\n')
    os.chmod(path, 0o600)
    try:
        r = subprocess.run([str(cli), '--connect', '--controller=' + a.controller, '--output-json', '--file=' + str(path)], capture_output=True, text=True)
        out = r.stdout + r.stderr
        if r.returncode or '"outcome" : "failed"' in out:
            (privado / 'ultimo-error.txt').write_text(out.replace(password, '[oculto]'))
            raise RuntimeError('Falló ' + etiqueta + '. Detalle privado: .local-security/ultimo-error.txt')
        print(etiqueta + ': aplicado')
    finally:
        path.unlink(missing_ok=True)

aplicar(cmds, 'TLS, listeners, identidad PMS y pool PostgreSQL')
aplicar((root / 'config/notificaciones-reintentos.cli').read_text().splitlines(), 'Cola de errores y reintentos de notificaciones')
aplicar((root / 'config/seguridad-a-jms.cli').read_text().splitlines(), 'Límites de direcciones JMS')
os.chmod(a.wildfly_home / 'standalone/configuration/standalone-full.xml', 0o600)
print('Copia anterior:', backup.relative_to(root))
print('Certificados locales:', certs.relative_to(root))
print('Hoteles PMS:', ','.join(map(str, hoteles)) or 'ninguno: SOAP bloqueado')
print('Configuración lista; requiere reload antes de desplegar. No se modificó la BD.')
