"""Git-tree membership and exact worktree-byte binding for the H7 guard."""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess


def candidate_files(root: Path, pattern: str):
    """Filesystem census of this checkout, including untracked production inputs.

    Top-level Git metadata and sibling linked checkouts are not this candidate.
    Do not apply Git-ignore filtering or suppress similarly named directories
    inside a production module: those can still be compiler inputs.
    """
    for entry in sorted(root.iterdir()):
        if entry.name in {'.git', '.worktrees'}:
            continue
        if entry.is_dir():
            yield from entry.rglob(pattern)
        elif entry.match(pattern):
            yield entry


def validate_entries(entries):
    seen = set()
    for mode, blob, name in entries:
        p = PurePosixPath(name)
        if (not name or p.is_absolute() or '..' in p.parts
                or p.as_posix() != name or name in seen):
            raise RuntimeError('duplicate or escaping input identity')
        if not re.fullmatch(r'[0-9a-f]{40}', blob):
            raise RuntimeError('unsupported Git object identity')
        seen.add(name)
    return entries


class Snapshot:
    """Membership is from one resolved tree, bytes are verified candidate files.

    The receipt hashes raw bytes. Java text uses the prior read_text universal
    newline decoding. Revalidation proves bounded endpoint equality, not the
    absence of a transient write that was restored between observations.
    """
    def __init__(self, root: Path, tree: str):
        root = Path(root).absolute()
        if root.is_symlink() or root.resolve() != root or not root.is_dir():
            raise RuntimeError('unresolved or symlink repository root')
        self.root = root
        if Path(self.git('rev-parse', '--show-toplevel').decode().strip()) != root:
            raise RuntimeError('root is not the explicit repository top level')
        if self.git('rev-parse', '--show-object-format').strip() != b'sha1':
            raise RuntimeError('unsupported object format')
        if not tree or tree.startswith('-') or '\0' in tree:
            raise RuntimeError('invalid tree binding')
        self.tree = self.git('rev-parse', '--verify', tree+'^{tree}').decode().strip()
        rows = []
        for item in self.git('ls-tree', '-rz', '--full-tree', self.tree).split(b'\0'):
            if not item:
                continue
            meta, name = item.split(b'\t', 1)
            mode, kind, blob = meta.decode('ascii').split()
            path = name.decode('utf-8')
            rows.append((mode, blob, path))
        validate_entries(rows)
        self.entries = sorted((mode, blob, name) for mode, blob, name in rows
                              if name == 'settings.gradle.kts' or
                              (name.endswith('.java') and '/src/main/java/' in '/'+name
                               and '/build/' not in '/'+name and '/generated/' not in '/'+name))
        self.entries.sort(key=lambda row: row[2])
        if not any(n == 'settings.gradle.kts' for _, _, n in self.entries):
            raise RuntimeError('required settings.gradle.kts missing from tree')
        self.raw = self.read()
        self.sources = {name: io.StringIO(data.decode('utf-8'), newline=None).read()
                        for name, data in self.raw.items() if name.endswith('.java')}
        if not self.sources:
            raise RuntimeError('no production Java inventory')

    def git(self, *args):
        env = {k: v for k, v in os.environ.items() if not k.startswith('GIT_')}
        env.update(GIT_OPTIONAL_LOCKS='0', GIT_NO_REPLACE_OBJECTS='1',
                   GIT_NO_LAZY_FETCH='1', GIT_TERMINAL_PROMPT='0')
        result = subprocess.run(['git', '-C', str(self.root), *args],
                                env=env, capture_output=True)
        if result.returncode:
            raise RuntimeError('Git input binding failed: '+args[0])
        return result.stdout

    def read(self):
        result = {}
        for mode, blob, name in self.entries:
            if mode not in ('100644', '100755'):
                raise RuntimeError('unsupported symlink or non-file input: '+name)
            path = self.root/name
            for component in (path, *path.parents):
                if component == self.root:
                    break
                if component.is_symlink():
                    raise RuntimeError('symlink input: '+name)
            if not path.resolve().is_relative_to(self.root):
                raise RuntimeError('input path escape: '+name)
            try:
                before = path.stat()
                if not stat.S_ISREG(before.st_mode):
                    raise RuntimeError('non-regular input: '+name)
                data = path.read_bytes()
                after = path.stat()
            except OSError as error:
                raise RuntimeError('missing or unreadable input: '+name) from error
            fields = ('st_dev', 'st_ino', 'st_size', 'st_mtime_ns', 'st_ctime_ns')
            if any(getattr(before,k) != getattr(after,k) for k in fields):
                raise RuntimeError('input drift during read: '+name)
            actual = hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
            if actual != blob:
                raise RuntimeError('candidate content differs from bound tree: '+name)
            result[name] = data
        return result

    def verify(self):
        if self.read() != self.raw:
            raise RuntimeError('input drift after capture')

    def receipt(self, selected):
        self.verify()
        return {'schema': 'h7-git-tree-input-v1', 'repository': str(self.root),
                'tree': self.tree, 'membership': 'git ls-tree of exact resolved tree',
                'content': 'candidate filesystem raw bytes equal Git blobs',
                'text_decoding': 'UTF-8 with universal newline conversion (unchanged)',
                'inputs': [{'path': name, 'git_blob': blob,
                            'sha256': hashlib.sha256(self.raw[name]).hexdigest(),
                            'selected': name in selected}
                           for _, blob, name in self.entries],
                'selected_paths': list(selected),
                'selected_text_sha256': {n: hashlib.sha256(v.encode()).hexdigest()
                                         for n,v in selected.items()},
                'observation': 'capture and post-evaluation revalidation; not continuous immutability'}
