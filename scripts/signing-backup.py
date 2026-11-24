"""Encrypted signing backup. Requires cryptography; never prints key material."""
import argparse
import base64
import getpass
import json
import os
from pathlib import Path
import secrets

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt

HEADER = b"MambaPad signing backup v1"


def key(password, salt):
    return Scrypt(salt=salt, length=32, n=32768, r=8, p=1).derive(password.encode())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["create", "verify", "restore"])
    parser.add_argument("backup", type=Path)
    parser.add_argument("--keystore", type=Path)
    parser.add_argument("--recovery-file", type=Path)
    parser.add_argument("--credentials-output", type=Path,
                        help="Private JSON destination for restored passwords/alias")
    args = parser.parse_args()
    if args.operation == "create":
        if not args.keystore:
            parser.error("--keystore is required")
        password = secrets.token_urlsafe(48) if args.recovery_file else getpass.getpass("Backup password: ")
        if len(password) < 20:
            parser.error("Use a backup password of at least 20 characters")
        payload = json.dumps({
            "keystore": base64.b64encode(args.keystore.read_bytes()).decode(),
            "alias": os.environ.get("MAMBAPAD_KEY_ALIAS") or input("Key alias: "),
            "store_password": os.environ.get("MAMBAPAD_STORE_PASSWORD") or getpass.getpass("Store password: "),
            "key_password": os.environ.get("MAMBAPAD_KEY_PASSWORD") or getpass.getpass("Key password: "),
        }).encode()
        salt, nonce = secrets.token_bytes(16), secrets.token_bytes(12)
        encrypted = AESGCM(key(password, salt)).encrypt(nonce, payload, HEADER)
        # Exclusive creation avoids destroying an existing recovery set.
        if args.recovery_file:
            with args.recovery_file.open("x", encoding="utf-8") as file:
                file.write(password + "\n")
        with args.backup.open("xb") as file:
            file.write(HEADER + salt + nonce + encrypted)
    else:
        password = args.recovery_file.read_text().strip() if args.recovery_file else getpass.getpass("Backup password: ")
        data = args.backup.read_bytes()
        if not data.startswith(HEADER):
            raise ValueError("Unknown backup format")
        data = data[len(HEADER):]
        payload = json.loads(AESGCM(key(password, data[:16])).decrypt(data[16:28], data[28:], HEADER))
        original = base64.b64decode(payload["keystore"], validate=True)
        if args.operation == "verify":
            if not args.keystore or original != args.keystore.read_bytes():
                raise ValueError("Restored keystore differs from original")
        else:
            if not args.keystore or not args.credentials_output:
                parser.error("restore requires --keystore and --credentials-output")
            if args.keystore.exists() or args.credentials_output.exists():
                raise FileExistsError("Restore destinations must not exist")
            with args.credentials_output.open("x", encoding="utf-8") as file:
                file.write(json.dumps({k: v for k, v in payload.items() if k != "keystore"}))
            with args.keystore.open("xb") as file:
                file.write(original)
    print("Signing backup " + args.operation + ": OK (no secrets displayed)")


if __name__ == "__main__":
    main()
