"""Focused qualification of the production input boundary (no external census)."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent

def guard():
    spec = importlib.util.spec_from_file_location('h7_guard_test', HERE/'h7-architecture-guard.py')
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module

class BoundaryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        self.git('init', '-q')
        self.put('settings.gradle.kts', 'rootProject.name = "fixture"\n')
        self.name = 'timeline-module/src/main/java/Legitimate.java'
        self.put(self.name, 'class Legitimate {}\n')
        self.tree = self.freeze()
    def tearDown(self):
        self.tmp.cleanup()
    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.root), *args]).decode().strip()
    def put(self, name, text):
        p=self.root/name;p.parent.mkdir(parents=True, exist_ok=True);p.write_text(text)
    def freeze(self):
        self.git('add', '--all')
        return self.git('write-tree')
    def snapshot(self):
        from h7_input_boundary import Snapshot
        return Snapshot(self.root, self.tree)
    def test_exact_bytes_and_order(self):
        self.put('timeline-module/src/main/java/A.java', 'class A {}\r\n')
        self.tree=self.freeze();s=self.snapshot()
        self.assertEqual(list(s.sources), sorted(s.sources))
        self.assertEqual(s.sources['timeline-module/src/main/java/A.java'], 'class A {}\n')
        s.verify()
    def test_nested_copy_excluded_by_membership(self):
        self.put('.worktrees/copy/'+self.name, 'class Evil { String table = "timeline_revision_ref"; }')
        self.assertEqual(list(guard().production_sources(self.root, self.snapshot())), [self.name])
    def test_similar_legitimate_names(self):
        self.put('timeline-module/src/main/java/worktrees/Canonical.java','class Canonical {}')
        self.tree=self.freeze();self.assertEqual(len(self.snapshot().sources),2)
    def test_missing(self):
        (self.root/self.name).unlink()
        with self.assertRaises(RuntimeError):self.snapshot()
    def test_changed(self):
        self.put(self.name,'class Changed {}')
        with self.assertRaises(RuntimeError):self.snapshot()
    def test_drift_after_capture(self):
        s=self.snapshot();self.put(self.name,'class Changed {}')
        with self.assertRaises(RuntimeError):s.verify()
    def test_context_drift(self):
        self.put('settings.gradle.kts','changed')
        with self.assertRaises(RuntimeError):self.snapshot()
    def test_symlink(self):
        (self.root/self.name).unlink();(self.root/self.name).symlink_to('/etc/hostname')
        with self.assertRaises(RuntimeError):self.snapshot()
    def test_parent_symlink(self):
        d=self.root/'timeline-module/src/main/java';d.rename(d.with_name('saved'));d.symlink_to('saved')
        with self.assertRaises(RuntimeError):self.snapshot()
    def test_unresolved_root(self):
        from h7_input_boundary import Snapshot
        with self.assertRaises(RuntimeError):Snapshot(self.root/'timeline-module',self.tree)
    def test_duplicate_and_escape(self):
        from h7_input_boundary import validate_entries
        row=('100644','a'*40,self.name)
        with self.assertRaises(RuntimeError):validate_entries([row,row])
        for path in ['../escape','/escape','x/../escape','x//escape','./escape']:
            with self.subTest(path=path),self.assertRaises(RuntimeError):validate_entries([('100644','a'*40,path)])
    def test_unresolved_tree(self):
        from h7_input_boundary import Snapshot
        with self.assertRaises(RuntimeError):Snapshot(self.root,'absent-tree')
    def test_required_context_missing(self):
        (self.root/'settings.gradle.kts').unlink();self.tree=self.freeze()
        with self.assertRaises(RuntimeError):self.snapshot()
    def test_real_violation_not_suppressed(self):
        g=guard();self.put(self.name,'class Legitimate { String x = "ProductCurrentRevisionService"; }')
        self.tree=self.freeze();s=self.snapshot()
        self.assertFalse(g.evaluate(g.production_sources(self.root,s)).passed)
    def test_tracked_symlink_rejected(self):
        (self.root/self.name).unlink();(self.root/self.name).symlink_to('settings.gradle.kts')
        self.tree=self.freeze()
        with self.assertRaises(RuntimeError):self.snapshot()

if __name__=='__main__':unittest.main(verbosity=2)
