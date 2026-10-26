import unittest,tempfile
from pathlib import Path
from unittest.mock import patch
from cli import manifest

class SourceManifestTest(unittest.TestCase):
    def test_git_file_and_directory_are_metadata_but_generated_files_are_not_ignored(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);(root/'source').write_bytes(b'accepted');(root/'.git').write_text('gitdir: /external/private-metadata')
            with patch('cli.WORK',root):
                linked=manifest();self.assertNotIn(b'.git',linked)
                (root/'.git').unlink();(root/'.git').mkdir();(root/'.git/index').write_bytes(b'metadata')
                self.assertEqual(linked,manifest())
                (root/'unexpected.pyc').write_bytes(b'unknown')
                self.assertNotEqual(linked,manifest());self.assertIn(b'unexpected.pyc',manifest())

if __name__=='__main__':unittest.main()
