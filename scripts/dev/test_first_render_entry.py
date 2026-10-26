"""Output guards must reject before tools, credentials, build or resource creation."""
from pathlib import Path
import subprocess,sys,tempfile,unittest,os
ROOT=Path(__file__).resolve().parents[2]
class OutputGuardTest(unittest.TestCase):
    def reject(self,path):
        result=subprocess.run([sys.executable,'-I','-B',str(ROOT/'scripts/dev/first-render'),'--output',str(path)],env={'PATH':'/usr/bin:/bin'},capture_output=True,text=True,timeout=10)
        self.assertEqual(2,result.returncode,result.stderr)
        self.assertFalse(path.exists())
    def test_source_descendant_is_rejected(self):
        self.reject(ROOT/'.validation-guard-probe')
    def test_dotdot_cannot_enter_source(self):
        self.reject(Path('/tmp')/'..'/str(ROOT).lstrip('/')/'.validation-guard-probe')
    def test_symlink_to_source_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            link=Path(d)/'source';link.symlink_to(ROOT,target_is_directory=True)
            self.reject(link/'.validation-guard-probe')
if __name__=='__main__':unittest.main()
