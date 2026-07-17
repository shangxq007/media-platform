#!/usr/bin/env python3
"""Hermes Governance Receipt Writer Daemon.

Runs as root under systemd. Accepts structured JSON receipt submissions
via Unix socket, validates against schema, and writes atomically to
the root-owned receipt store.

Usage:
    hermes_receipt_writer.py [--socket PATH] [--receipt-store PATH] [--help]

This daemon is NOT intended to be run manually. It is managed by
hermes-receipt-writer.service.
"""

import argparse
import hashlib
import json
import logging
import os
import socket
import struct
import sys
import tempfile
import time
from pathlib import Path

# --- Constants ---
RECEIPT_STORE = Path("/var/lib/hermes/receipts")
SOCKET_PATH = Path("/var/run/hermes-receipt-writer/receipt-writer.sock")
CREDENTIAL_PATH = os.environ.get("CREDENTIALS_DIRECTORY", "")

VALID_DECISIONS = {"PASS", "FAIL"}
REJECTED_DECISIONS = {"CONDITIONAL_PASS", "PASS_WITH_SAME_UID_RISK",
                       "PASS_WITH_UNTESTED_WRITER", "PASS_WITH_REBOOT_ASSUMED",
                       "PASS_WITH_UNMOUNT_FAILURE"}
VALID_RECEIPT_TYPES = {"REVIEW", "VERIFICATION", "CONTROL_PLANE_REVIEW",
                        "CONTROL_PLANE_VERIFICATION"}

LOG_FORMAT = "%(asctime)s [receipt-writer] %(levelname)s %(message)s"

# --- Schema validation ---
REQUIRED_FIELDS = [
    "task", "receipt_type", "subject_commit", "subject_tree",
    "decision", "run_id", "worktree", "completed_at"
]

OPTIONAL_FIELDS = [
    "reviewer_run_id", "reviewer_worktree", "review_receipt_path",
    "review_receipt_sha256", "install_manifest_sha256", "systemd_unit_hashes",
    "policy_hashes", "receipt_writer_hash", "protected_path_state",
    "host_id", "boot_id"
]


def validate_receipt(data: dict) -> tuple[bool, str]:
    """Validate receipt data against schema. Returns (valid, error_message)."""
    # Check required fields
    for field in REQUIRED_FIELDS:
        if field not in data:
            return False, f"MISSING_REQUIRED_FIELD: {field}"

    # Validate decision
    decision = data.get("decision", "")
    if decision in REJECTED_DECISIONS:
        return False, f"REJECTED_DECISION: {decision}"
    if decision not in VALID_DECISIONS:
        return False, f"INVALID_DECISION: {decision}"

    # Validate receipt_type
    receipt_type = data.get("receipt_type", "")
    if receipt_type not in VALID_RECEIPT_TYPES:
        return False, f"INVALID_RECEIPT_TYPE: {receipt_type}"

    # Reject empty run_id
    if not data.get("run_id", "").strip():
        return False, "EMPTY_RUN_ID"

    # Reject wildcard worktree
    worktree = data.get("worktree", "")
    if "*" in worktree or "?" in worktree:
        return False, "WILDCARD_WORKTREE"
    if not worktree.strip():
        return False, "EMPTY_WORKTREE"

    # Validate commit hashes (must be 40-char hex)
    for field in ["subject_commit", "subject_tree"]:
        val = data.get(field, "")
        if not val or len(val) != 40 or not all(c in "0123456789abcdef" for c in val):
            return False, f"INVALID_HASH: {field}={val}"

    return True, ""


def verify_review_before_verification(data: dict, receipt_store: Path) -> tuple[bool, str]:
    """For verification receipts, check that review receipt exists and is PASS."""
    receipt_type = data.get("receipt_type", "")
    if "VERIFICATION" not in receipt_type:
        return True, ""  # Not a verification receipt, no ordering check needed

    # Need review_receipt_path and review_receipt_sha256
    review_path = data.get("review_receipt_path", "")
    review_sha256 = data.get("review_receipt_sha256", "")

    if not review_path:
        return False, "PERSISTENT_REVIEW_RECEIPT_MISSING"

    review_file = Path(review_path)
    if not review_file.exists():
        return False, "PERSISTENT_REVIEW_RECEIPT_MISSING"

    # Verify hash
    if review_sha256:
        actual_hash = hashlib.sha256(review_file.read_bytes()).hexdigest()
        if actual_hash != review_sha256:
            return False, "PERSISTENT_REVIEW_RECEIPT_HASH_MISMATCH"

    # Read review receipt and check decision
    try:
        review_data = json.loads(review_file.read_text())
        if review_data.get("decision") != "PASS":
            return False, "PERSISTENT_REVIEW_NOT_PASS"

        # Check temporal ordering
        review_completed = review_data.get("completed_at", 0)
        verification_started = data.get("completed_at", 0)
        if review_completed > verification_started:
            return False, "PERSISTENT_REVIEW_TOO_LATE"

        # Check same task/subject
        if review_data.get("task") != data.get("task"):
            return False, "PERSISTENT_REVIEW_SUBJECT_MISMATCH"
        if review_data.get("subject_commit") != data.get("subject_commit"):
            return False, "PERSISTENT_REVIEW_SUBJECT_MISMATCH"

    except (json.JSONDecodeError, KeyError) as e:
        return False, f"PERSISTENT_REVIEW_READ_ERROR: {e}"

    return True, ""


def normalize_task_name(task: str) -> str:
    """Normalize task name for filename: lowercase, no special chars."""
    import re
    normalized = task.lower().strip()
    normalized = re.sub(r'[^a-z0-9\-]', '-', normalized)
    normalized = re.sub(r'-+', '-', normalized)
    normalized = normalized.strip('-')
    return normalized[:100]  # Cap length


def generate_receipt_filename(data: dict) -> str:
    """Generate deterministic receipt filename."""
    task = normalize_task_name(data.get("task", "unknown"))
    receipt_type = data.get("receipt_type", "unknown").lower()
    subject_hash = data.get("subject_commit", "nohash")[:16]
    return f"{task}--{receipt_type}--{subject_hash}.json"


def write_receipt(data: dict, receipt_store: Path) -> tuple[bool, str, str]:
    """Write receipt atomically. Returns (success, message, filepath)."""
    receipt_type = data.get("receipt_type", "")

    # Determine subdirectory
    if "CONTROL_PLANE" in receipt_type:
        subdir = receipt_store / "control-plane"
    elif "VERIFICATION" in receipt_type:
        subdir = receipt_store / "verification"
    elif "REVIEW" in receipt_type:
        subdir = receipt_store / "review"
    else:
        subdir = receipt_store / "rejected"

    filename = generate_receipt_filename(data)
    target = subdir / filename

    # Check if already exists (never overwrite)
    if target.exists():
        return False, "REJECT_ALREADY_EXISTS", str(target)

    # Compute payload hash
    payload_json = json.dumps(data, sort_keys=True, separators=(',', ':'))
    payload_hash = hashlib.sha256(payload_json.encode()).hexdigest()
    data["payload_sha256"] = payload_hash

    # Write to temporary file
    tmp_path = None
    try:
        fd, tmp_path = tempfile.mkstemp(
            dir=str(subdir),
            prefix=".receipt-",
            suffix=".tmp"
        )
        with os.fdopen(fd, 'w') as f:
            json.dump(data, f, indent=2, sort_keys=True)
            f.write('\n')
            f.flush()
            os.fsync(fd)

        # Atomic rename with O_EXCL
        os.link(tmp_path, str(target))  # Hard link (atomic, fails if exists)
        os.unlink(tmp_path)

        # fsync directory
        dir_fd = os.open(str(subdir), os.O_RDONLY)
        try:
            os.fsync(dir_fd)
        finally:
            os.close(dir_fd)

        return True, "OK", str(target)

    except FileExistsError:
        # Target was created between our check and rename
        if tmp_path:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass
        return False, "REJECT_ALREADY_EXISTS", str(target)
    except Exception as e:
        if tmp_path:
            try:
                os.unlink(tmp_path)
            except OSError:
                pass
        return False, f"WRITE_ERROR: {e}", ""


def rebuild_index(receipt_store: Path) -> bool:
    """Rebuild index from all immutable receipt files."""
    index_dir = receipt_store / "indexes"
    index_file = index_dir / "index.json"

    entries = []
    for subdir in ["review", "verification", "control-plane"]:
        subdir_path = receipt_store / subdir
        if not subdir_path.exists():
            continue
        for f in sorted(subdir_path.glob("*.json")):
            try:
                data = json.loads(f.read_text())
                entries.append({
                    "path": str(f),
                    "task": data.get("task"),
                    "receipt_type": data.get("receipt_type"),
                    "decision": data.get("decision"),
                    "subject_commit": data.get("subject_commit"),
                    "completed_at": data.get("completed_at"),
                    "payload_sha256": data.get("payload_sha256"),
                })
            except (json.JSONDecodeError, KeyError):
                continue

    index_data = {
        "rebuilt_at": time.time(),
        "entry_count": len(entries),
        "entries": entries,
    }

    # Atomic write
    fd, tmp_path = tempfile.mkstemp(dir=str(index_dir), prefix=".index-", suffix=".tmp")
    try:
        with os.fdopen(fd, 'w') as f:
            json.dump(index_data, f, indent=2, sort_keys=True)
            f.write('\n')
            f.flush()
            os.fsync(fd)
        os.replace(tmp_path, str(index_file))
        dir_fd = os.open(str(index_dir), os.O_RDONLY)
        try:
            os.fsync(dir_fd)
        finally:
            os.close(dir_fd)
        return True
    except Exception:
        try:
            os.unlink(tmp_path)
        except OSError:
            pass
        return False


def verify_peer_credential(client_sock: socket.socket) -> tuple[bool, dict]:
    """Verify peer credential via SO_PEERCRED. Returns (valid, creds)."""
    try:
        creds = client_sock.getsockopt(socket.SOL_SOCKET, socket.SO_PEERCRED,
                                        struct.calcsize('3i'))
        pid, uid, gid = struct.unpack('3i', creds)
        return True, {"pid": pid, "uid": uid, "gid": gid}
    except Exception as e:
        return False, {"error": str(e)}


def load_service_credential(credential_dir: str) -> str:
    """Load service credential from systemd LoadCredential directory."""
    if not credential_dir:
        return ""
    cred_path = Path(credential_dir) / "receipt-writer-token"
    if cred_path.exists():
        return cred_path.read_text().strip()
    return ""


def handle_client(client_sock: socket.socket, receipt_store: Path,
                   service_token: str, logger: logging.Logger):
    """Handle a single client connection."""
    try:
        # Verify peer credential
        valid, creds = verify_peer_credential(client_sock)
        if not valid:
            response = {"status": "REJECTED", "reason": "PEER_CREDENTIAL_FAILED"}
            client_sock.sendall(json.dumps(response).encode())
            return

        peer_uid = creds.get("uid", -1)
        peer_pid = creds.get("pid", -1)

        # Read data
        data = b""
        while True:
            chunk = client_sock.recv(4096)
            if not chunk:
                break
            data += chunk
            if len(data) > 65536:  # Max 64KB
                break

        if not data:
            response = {"status": "REJECTED", "reason": "EMPTY_DATA"}
            client_sock.sendall(json.dumps(response).encode())
            return

        # Parse JSON
        try:
            receipt_data = json.loads(data.decode())
        except json.JSONDecodeError as e:
            response = {"status": "REJECTED", "reason": f"INVALID_JSON: {e}"}
            client_sock.sendall(json.dumps(response).encode())
            return

        # Add metadata
        receipt_data["_peer_uid"] = peer_uid
        receipt_data["_peer_pid"] = peer_pid
        receipt_data["_received_at"] = time.time()

        # Validate schema
        valid, error = validate_receipt(receipt_data)
        if not valid:
            # Write to rejected/
            receipt_data["_rejection_reason"] = error
            rejected_dir = receipt_store / "rejected"
            filename = generate_receipt_filename(receipt_data)
            rejected_path = rejected_dir / filename
            try:
                rejected_path.write_text(json.dumps(receipt_data, indent=2))
            except Exception:
                pass

            response = {"status": "REJECTED", "reason": error}
            logger.warning(f"Rejected from UID {peer_uid} PID {peer_pid}: {error}")
            client_sock.sendall(json.dumps(response).encode())
            return

        # Verify review-before-verification ordering
        valid, error = verify_review_before_verification(receipt_data, receipt_store)
        if not valid:
            receipt_data["_rejection_reason"] = error
            rejected_dir = receipt_store / "rejected"
            filename = generate_receipt_filename(receipt_data)
            try:
                (rejected_dir / filename).write_text(json.dumps(receipt_data, indent=2))
            except Exception:
                pass

            response = {"status": "REJECTED", "reason": error}
            logger.warning(f"Ordering rejected from UID {peer_uid}: {error}")
            client_sock.sendall(json.dumps(response).encode())
            return

        # Write receipt
        success, message, filepath = write_receipt(receipt_data, receipt_store)
        if success:
            logger.info(f"Accepted from UID {peer_uid} PID {peer_pid}: {filepath}")
            response = {"status": "OK", "path": filepath,
                        "payload_sha256": receipt_data.get("payload_sha256")}
        else:
            logger.warning(f"Write failed from UID {peer_uid}: {message}")
            response = {"status": "REJECTED", "reason": message}

        client_sock.sendall(json.dumps(response).encode())

    except Exception as e:
        logger.error(f"Client handler error: {e}")
        try:
            response = {"status": "ERROR", "reason": str(e)}
            client_sock.sendall(json.dumps(response).encode())
        except Exception:
            pass
    finally:
        client_sock.close()


def main():
    parser = argparse.ArgumentParser(description="Hermes Governance Receipt Writer Daemon")
    parser.add_argument("--socket", type=str, default=str(SOCKET_PATH),
                        help="Unix socket path")
    parser.add_argument("--receipt-store", type=str, default=str(RECEIPT_STORE),
                        help="Receipt store directory")
    parser.add_argument("--rebuild-index", action="store_true",
                        help="Rebuild index and exit")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format=LOG_FORMAT)
    logger = logging.getLogger("receipt-writer")

    receipt_store = Path(args.receipt_store)
    if not receipt_store.exists():
        logger.error(f"Receipt store does not exist: {receipt_store}")
        sys.exit(1)

    if args.rebuild_index:
        success = rebuild_index(receipt_store)
        sys.exit(0 if success else 1)

    # Load service credential
    service_token = load_service_credential(CREDENTIAL_PATH)
    if not service_token:
        logger.warning("No service credential loaded")

    socket_path = Path(args.socket)
    socket_path.parent.mkdir(parents=True, exist_ok=True)

    # Remove stale socket
    if socket_path.exists():
        socket_path.unlink()

    # Create Unix socket
    sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    sock.bind(str(socket_path))
    sock.listen(5)

    # Set socket permissions (root-owned, readable by UID 1000 for submission)
    os.chmod(str(socket_path), 0o660)
    os.chown(str(socket_path), 0, 0)

    logger.info(f"Listening on {socket_path}")
    logger.info(f"Receipt store: {receipt_store}")

    try:
        while True:
            client_sock, _ = sock.accept()
            handle_client(client_sock, receipt_store, service_token, logger)
    except KeyboardInterrupt:
        logger.info("Shutting down")
    finally:
        sock.close()
        socket_path.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
