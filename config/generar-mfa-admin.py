#!/usr/bin/env python3
"""Genera en .local-security el secreto y URI TOTP de una cuenta administradora."""
import argparse
import base64
import os
from pathlib import Path
import secrets
from urllib.parse import quote, urlencode

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--usuario-id", type=int, required=True)
parser.add_argument("--email", required=True)
args = parser.parse_args()
if args.usuario_id <= 0:
    parser.error("usuario-id debe ser positivo")
if "@" not in args.email or len(args.email) > 160:
    parser.error("email inválido")

os.umask(0o077)
destino = Path(__file__).resolve().parent.parent / ".local-security"
destino.mkdir(mode=0o700, exist_ok=True)
archivo = destino / f"mfa-admin-{args.usuario_id}.txt"
if archivo.exists():
    parser.error(f"{archivo} ya existe; no se reemplazó el secreto")

secreto = base64.b32encode(secrets.token_bytes(20)).decode("ascii").rstrip("=")
etiqueta = quote(f"StayHub:{args.email}", safe="")
uri = "otpauth://totp/" + etiqueta + "?" + urlencode({
    "secret": secreto, "issuer": "StayHub", "algorithm": "SHA1", "digits": 6, "period": 30
})
variable = f"STAYHUB_MFA_ADMIN_{args.usuario_id}_SECRET"
archivo.write_text(f"{variable}={secreto}\n{uri}\n", encoding="utf-8")
os.chmod(archivo, 0o600)
print(f"MFA generado en {archivo.relative_to(destino.parent)} (permisos 0600).")
print("Importá la URI en el autenticador y configurá la variable en el proceso de WildFly.")
