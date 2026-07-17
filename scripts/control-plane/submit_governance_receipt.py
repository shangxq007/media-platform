#!/usr/bin/env python3
"""Submit a governance receipt to the Hermes receipt writer daemon.

Usage:
    submit_governance_receipt.py --receipt-file PATH [--socket PATH] [--help]
    submit_governance_receipt.py --json '{"task":...}' [--socket PATH]

Reads a JSON receipt from file or stdin and submits it to the receipt writer
via Unix socket. Prints the writer's response as JSON.
"""

import argparse
import json
import os
import socket
import struct
import sys
from pathlib import Path

SOCKET_PATH = Path("/var/run/hermes-receipt-writer/receipt-writer.sock")


def submit_receipt(data: dict, socket_path: Path) -> dict:
    """Submit receipt to writer daemon. Returns response dict."""
    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    try:
        sock.connect(str(socket_path))
        payload = json.dumps(data).encode()
        sock.sendall(payload)
        sock.shutdown(socket.SHUT_WR)

        response = b""
        while True:
            chunk = sock.recv(4096)
            if not chunk:
                break
            response += chunk

        if not response:
            return {"status": "ERROR", "reason": "EMPTY_RESPONSE"}

        return json.loads(response.decode())
    except ConnectionRefusedError:
        return {"status": "ERROR", "reason": "WRITER_NOT_RUNNING"}
    except FileNotFoundError:
        return {"status": "ERROR", "reason": f"SOCKET_NOT_FOUND: {socket_path}"}
    except Exception as e:
        return {"status": "ERROR", "reason": str(e)}
    finally:
        sock.close()


def main():
    parser = argparse.ArgumentParser(description="Submit governance receipt")
    parser.add_argument("--receipt-file", type=str, help="Path to receipt JSON file")
    parser.add_argument("--json", type=str, help="Receipt JSON string")
    parser.add_argument("--socket", type=str, default=str(SOCKET_PATH),
                        help="Receipt writer socket path")
    args = parser.parse_args()

    # Load receipt data
    if args.receipt_file:
        path = Path(args.receipt_file)
        if not path.exists():
            print(json.dumps({"status": "ERROR", "reason": f"FILE_NOT_FOUND: {path}"}))
            sys.exit(1)
        data = json.loads(path.read_text())
    elif args.json:
        data = json.loads(args.json)
    elif not sys.stdin.isatty():
        data = json.loads(sys.stdin.read())
    else:
        print(json.dumps({"status": "ERROR", "reason": "NO_INPUT"}))
        sys.exit(1)

    socket_path = Path(args.socket)
    response = submit_receipt(data, socket_path)
    print(json.dumps(response, indent=2))
    sys.exit(0 if response.get("status") == "OK" else 1)


if __name__ == "__main__":
    main()
