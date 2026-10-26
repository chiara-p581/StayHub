#!/usr/bin/env python3
"""Aplica KAN-48/51 usando el datasource local y provisiona TOTP para todos los ADMIN."""
import argparse
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--wildfly-home", required=True, type=Path)
parser.add_argument("--jdbc-driver", required=True, type=Path)
parser.add_argument("--controller", default="localhost:9990")
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
cli = args.wildfly_home / "bin/jboss-cli.sh"

def ejecutar(comando, *, env=None):
    resultado = subprocess.run([str(x) for x in comando], capture_output=True, text=True, env=env)
    if resultado.returncode:
        detalle = (resultado.stderr or resultado.stdout)[-4000:]
        if env:
            for nombre in ("STAYHUB_SETUP_DB_URL", "STAYHUB_SETUP_DB_USER", "STAYHUB_SETUP_DB_PASSWORD"):
                valor = env.get(nombre)
                if valor: detalle = detalle.replace(valor, "[oculto]")
        raise RuntimeError("Falló una herramienta de configuración: " + detalle.strip())
    return resultado.stdout

salida = ejecutar([cli, "--connect", "--controller=" + args.controller, "--output-json",
                   "--commands=/subsystem=datasources/data-source=StayHubDS:read-resource(include-runtime=false,recursive=true)"])
inicio = salida.find("{")
datos = json.loads(salida[inicio:])["result"]
password = datos.get("password")
if not password and isinstance(datos.get("credential-reference"), dict):
    password = datos["credential-reference"].get("clear-text")
if not password:
    raise RuntimeError("El datasource no expone una credencial utilizable para la migración")

entorno = dict(os.environ)
url = datos["connection-url"]
separador = "&" if "?" in url else "?"
url += separador + "connectTimeout=15&socketTimeout=20"
entorno.update({
    "STAYHUB_SETUP_DB_URL": url,
    "STAYHUB_SETUP_DB_USER": datos["user-name"],
    "STAYHUB_SETUP_DB_PASSWORD": password,
})
java = ejecutar(["java", "--class-path", args.jdbc_driver,
                 root / "config/AplicarControlesSeguridad.java",
                 root / "config/seguridad-controles-schema.sql"], env=entorno)
administradores = []
for linea in java.splitlines():
    partes = linea.split("\t")
    if len(partes) == 3 and partes[0] == "ADMIN":
        administradores.append((int(partes[1]), partes[2]))
if not administradores:
    raise RuntimeError("La base no contiene cuentas ADMIN; no se puede provisionar MFA")

for usuario_id, email in administradores:
    archivo = root / ".local-security" / f"mfa-admin-{usuario_id}.txt"
    if not archivo.exists():
        ejecutar([sys.executable, root / "config/generar-mfa-admin.py",
                  "--usuario-id", usuario_id, "--email", email])

privado = root / ".local-security"
secretos_tls = json.loads((privado / "secretos.json").read_text(encoding="utf-8"))
lineas = ["#!/bin/sh", "set -eu",
          "export STAYHUB_TLS_STORE_PASSWORD=" + shlex.quote(secretos_tls["tls_store_password"])]
for usuario_id, _ in administradores:
    primera = (privado / f"mfa-admin-{usuario_id}.txt").read_text(encoding="utf-8").splitlines()[0]
    nombre, secreto = primera.split("=", 1)
    lineas.append("export " + nombre + "=" + shlex.quote(secreto))
lineas.append("exec " + shlex.quote(str(args.wildfly_home / "bin/standalone.sh")) + " -c standalone-full.xml")
inicio_seguro = privado / "iniciar-wildfly-seguro.sh"
inicio_seguro.write_text("\n".join(lineas) + "\n", encoding="utf-8")
os.chmod(inicio_seguro, 0o700)
print("Migración verificada y MFA generado para " + str(len(administradores)) + " administrador(es).")
print("Inicio seguro preparado en .local-security/iniciar-wildfly-seguro.sh")
