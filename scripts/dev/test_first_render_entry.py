"""Output and provider guards must reject before tools, credentials, build or resource creation."""
from pathlib import Path
import subprocess,sys,tempfile,unittest,os,shutil
ROOT=Path(__file__).resolve().parents[2]
ENTRY=ROOT/'scripts/dev/first-render'
BMF_SOURCE='8fb3ec03b06ba56e5276a6844614879cdc0ae3886b42a4bf783782708130e327'
BMF_BUNDLE='0d4c2e9220a582f9ce286f7185f513ca71a0272910d0e79da8a226d734a03be1'
class OutputGuardTest(unittest.TestCase):
    def reject(self,path):
        result=subprocess.run([sys.executable,'-I','-B',str(ENTRY),'--output',str(path)],env={'PATH':'/usr/bin:/bin'},capture_output=True,text=True,timeout=10)
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
class BmfProviderGuardTest(unittest.TestCase):
    """The BMF provider entry must fail closed on its own preflight, before any output is created."""
    def reject(self,arguments):
        with tempfile.TemporaryDirectory() as d:
            output=Path(d)/'runtime'
            result=subprocess.run([sys.executable,'-I','-B',str(ENTRY),'--output',str(output),*arguments],env={'PATH':'/usr/bin:/bin'},capture_output=True,text=True,timeout=120)
            self.assertEqual(2,result.returncode,result.stdout+result.stderr)
            self.assertFalse(output.exists())
            return result
    def fake_runtime(self,directory):
        root=Path(directory)/'bmf-runtime';root.mkdir()
        (root/'runtime-manifest.properties').write_text('\n'.join([f'bmf.version=0.2.0',f'ffmpeg.version=4.3.7',f'python.version=3.9.2',f'numpy.version=1.19.5',f'source.sha256={BMF_SOURCE}',f'bundle.sha256={BMF_BUNDLE}'])+'\n')
        (root/'host-bundle/usr/bin').mkdir(parents=True);shutil.copyfile('/usr/bin/true',root/'host-bundle/usr/bin/python3.9');(root/'host-bundle/usr/bin/python3.9').chmod(0o755)
        (root/'host-bundle/usr/lib/python3.9').mkdir(parents=True)
        (root/'host-bundle/usr/lib/python3/dist-packages/numpy').mkdir(parents=True)
        (root/'output/bmf/lib').mkdir(parents=True);(root/'output/bmf/lib/libbuiltin_modules.so').write_bytes(b'not-the-pinned-library')
        return root
    def test_bmf_requires_an_explicit_runtime_root(self):
        self.reject(['--provider','bmf','--scenario','single-video-no-audio'])
    def test_bmf_runtime_root_must_be_an_existing_directory(self):
        self.reject(['--provider','bmf','--bmf-runtime-root','/tmp/definitely-not-a-bmf-runtime'])
    def test_wrong_bundle_digest_is_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            root=self.fake_runtime(d)
            result=self.reject(['--provider','bmf','--scenario','single-video-no-audio','--bmf-runtime-root',str(root)])
            self.assertIn('bundle digest',result.stderr)
    def test_bmf_runtime_root_without_the_provider_is_rejected(self):
        self.reject(['--provider','ffmpeg','--bmf-runtime-root','/tmp'])
    def test_bmf_rejects_the_operation_editing_flow(self):
        self.reject(['--provider','bmf','--timeline-operation-editing'])
if __name__=='__main__':
    unittest.main()
