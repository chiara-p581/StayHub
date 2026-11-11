#!/usr/bin/env python3
"""Migra StayHubDS de JDBC a XA conservando URL, usuario y contraseña privados.

Requiere comprobar antes que PostgreSQL permite PREPARE TRANSACTION.
Sin --apply solamente muestra el plan; crea respaldo privado antes de aplicar.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--wildfly-home', type=Path, required=True)
p.add_argument('--apply', action='store_true')
a = p.parse_args()
config = a.wildfly_home / 'standalone/configuration/standalone-full.xml'
tree = ET.parse(config)
local = lambda e: e.tag.rsplit('}', 1)[-1]
datasources = [e for e in tree.iter() if local(e) in ('datasource', 'xa-datasource') and e.get('pool-name') == 'StayHubDS']
if len(datasources) != 1:
    raise SystemExit('Debe existir exactamente un StayHubDS')
ds = datasources[0]
if local(ds) == 'xa-datasource':
    print('StayHubDS ya es XA; no se modifica.')
    raise SystemExit(0)
children = {local(e): e for e in ds}
url = children['connection-url'].text.strip()
driver = children['driver'].text.strip()
security = children['security']
user, password = security.get('user-name'), security.get('password')
if not user or password is None or '${' in url + user + password:
    raise SystemExit('Configuración indirecta: resolver manualmente sin exponer secretos')
print('Plan: StayHubDS -> PGXADataSource; mismo JNDI y URL TLS; pool máximo 8; respaldo privado y reload.')
if not a.apply:
    raise SystemExit(0)
os.umask(0o077)
root = Path(__file__).resolve().parent.parent
backup = root / '.local-security/backups' / datetime.datetime.now().strftime('%Y%m%d-%H%M%S-before-xa')
backup.mkdir(parents=True, mode=0o700)
shutil.copy2(config, backup / config.name)
os.chmod(backup / config.name, 0o600)
q = json.dumps
address = '/subsystem=datasources/xa-data-source=StayHubDS'
commands = [
    'batch',
    '/subsystem=datasources/data-source=StayHubDS:remove',
    address + ':add(jndi-name=' + q(ds.get('jndi-name')) + ',driver-name=' + q(driver)
    + ',xa-datasource-class="org.postgresql.xa.PGXADataSource",user-name=' + q(user)
    + ',password=' + q(password) + ',enabled=true,min-pool-size=0,max-pool-size=8,statistics-enabled=true'
    + ',blocking-timeout-wait-millis=5000,query-timeout=15,set-tx-query-timeout=true'
    + ',validate-on-match=true,valid-connection-checker-class-name="org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLValidConnectionChecker"'
    + ',exception-sorter-class-name="org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLExceptionSorter")',
    address + '/xa-datasource-properties=URL:add(value=' + q(url) + ')',
    address + '/xa-datasource-properties=ConnectTimeout:add(value="10")',
    address + '/xa-datasource-properties=SocketTimeout:add(value="30")',
    'run-batch', ':reload'
]
cli = a.wildfly_home / 'bin/jboss-cli.sh'
with tempfile.TemporaryDirectory(prefix='stayhub-xa-') as tmp:
    script = Path(tmp) / 'migration.cli'
    script.write_text('\n'.join(commands) + '\n')
    os.chmod(script, 0o600)
    result = subprocess.run([str(cli), '--connect', '--file=' + str(script)], capture_output=True, text=True)
    if result.returncode:
        output = (result.stdout + result.stderr).replace(password, '[oculto]')
        print(output[-6000:])
        raise SystemExit(result.returncode)
print('Migración XA aplicada. Respaldo: ' + str(backup))
