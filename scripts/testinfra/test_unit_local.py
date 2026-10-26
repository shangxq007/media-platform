"""Local database opt-in must not open the controller's host database or Secret."""
from pathlib import Path
import json
import os
import tempfile
import unittest
from unittest.mock import patch
from controlled_launch import birth,EXECUTOR
from state import PolicyError
import local_pg18

class LocalAdmission(unittest.TestCase):
    def test_host_network_binding_rejected_before_password_or_output(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);binding=root/'binding.json'
            data=dict(container_id='a'*64,identity=birth(os.getpid()),
                      network_namespace=os.readlink('/proc/self/ns/net'),
                      user_namespace=os.readlink('/proc/self/ns/user'),
                      source=str(EXECUTOR.parents[2]),runtime_dirs=[],ca=str(root/'ca'),
                      scratch=str(root/'scratch'),run='local',attempt='a1',launch='a'*32)
            binding.write_text(json.dumps(data));binding.chmod(0o600)
            output=root/'not-created'
            with patch.object(local_pg18,'private_file',side_effect=AssertionError('password must not be read')) as secret:
                with self.assertRaisesRegex(PolicyError,'host network'):
                    local_pg18.execute(binding,root/'secret',root/'classpath',output)
                secret.assert_not_called()
            self.assertFalse(output.exists())

    def test_secret_input_requires_private_regular_file(self):
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary);p=root/'secret';p.write_text('synthetic');p.chmod(0o644)
            with self.assertRaises(PolicyError):local_pg18.private_file(p)
            p.chmod(0o600);self.assertEqual(p,local_pg18.private_file(p))
            link=root/'link';link.symlink_to(p)
            with self.assertRaises(PolicyError):local_pg18.private_file(link)
