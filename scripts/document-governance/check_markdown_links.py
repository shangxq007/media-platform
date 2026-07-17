#!/usr/bin/env python3
"""Broken Link Guard (DG-011)"""

import hashlib
import json
import os
import re

def strip_front_matter(content):
    if content.startswith("---"):
        end = content.find("---", 3)
        if end != -1:
            return content[end+3:].lstrip("\n")
    return content

def scan_links(repo_root, commit_ref=None):
    """Scan docs/**/*.md for repository-relative links."""
    links = []
    md_files = []
    docs_root = os.path.join(repo_root, "docs")
    
    for root, dirs, files in os.walk(docs_root):
        if '.git' in dirs:
            dirs.remove('.git')
        for f in files:
            if f.endswith('.md'):
                md_files.append(os.path.join(root, f))
    
    for md_file in md_files:
        rel_path = os.path.relpath(md_file, repo_root)
        file_dir = os.path.dirname(md_file)
        
        with open(md_file, 'r', errors='ignore') as f:
            content = f.read()
        
        for match in re.finditer(r'\[([^\]]*)\]\(([^)]+)\)', content):
            target = match.group(2).strip()
            line_num = content[:match.start()].count('\n') + 1
            
            if any(target.startswith(p) for p in ['http://', 'https://', 'mailto:', 'ftp:', 'data:', 'javascript:', 'tel:']):
                continue
            if target.startswith('#'):
                continue
            
            path_part = target.split('#')[0].split('?')[0]
            if not path_part:
                continue
            
            path_part = path_part.replace('%20', ' ')
            resolved = os.path.normpath(os.path.join(file_dir, path_part))
            rel_resolved = os.path.relpath(resolved, repo_root)
            exists = os.path.exists(resolved)
            
            identity_key = hashlib.sha256(f"{rel_path}\n{rel_resolved}\n{'missing'}".encode()).hexdigest()[:16]
            
            links.append({
                "source_path": rel_path,
                "line": line_num,
                "raw_target": target,
                "normalized_target": path_part,
                "resolved_target": rel_resolved,
                "valid": exists,
                "reason": "missing" if not exists else "",
                "identity_key": identity_key if not exists else ""
            })
    
    return links

def check_broken_link_guard(head, base, repo_root):
    """DG-011: Broken-link increment guard."""
    baseline_path = os.path.join(repo_root, "docs/architecture/governance/automated-guards/broken-link-debt-baseline.json")
    
    current_links = scan_links(repo_root)
    current_broken = {l["identity_key"] for l in current_links if not l["valid"]}
    
    if os.path.exists(baseline_path):
        with open(baseline_path) as f:
            baseline = json.load(f)
        baseline_broken = {e["identity_key"] for e in baseline.get("entries", baseline.get("broken_links", []))}
    else:
        baseline_broken = set()
    
    introduced = current_broken - baseline_broken
    resolved = baseline_broken - current_broken
    
    violations = []
    if introduced:
        violations.append({
            "code": "INTRODUCED_BROKEN_LINK",
            "severity": "ERROR",
            "count": len(introduced),
            "message": f"{len(introduced)} new broken link(s) introduced"
        })
    
    return {
        "passed": len(violations) == 0,
        "violations": violations,
        "metrics": {
            "baseline_broken": len(baseline_broken),
            "current_broken": len(current_broken),
            "introduced": len(introduced),
            "resolved": len(resolved)
        }
    }
