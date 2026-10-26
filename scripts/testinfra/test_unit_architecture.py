"""Keep infrastructure boundaries and migrated business test bodies executable."""
import hashlib
import json
from pathlib import Path
import re
import socket
import errno
import unittest

WORK=Path(__file__).resolve().parents[2]

def test_bodies(text):
    mask=re.sub(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/',lambda m:' '*(m.end()-m.start()),text)
    result={}
    for match in re.finditer(r'@Test\b[\s\S]*?\bvoid\s+(\w+)\s*\([^)]*\)[^{]*\{',mask):
        start=match.end()-1;depth=1;i=start+1
        while depth:
            if mask[i]=='{':depth+=1
            elif mask[i]=='}':depth-=1
            i+=1
        result[match[1]]=hashlib.sha256(text[start:i].encode()).hexdigest()
    return result

class Boundaries(unittest.TestCase):
    def test_network_denial_is_actually_inherited(self):
        for family in (socket.AF_INET,socket.AF_INET6,socket.AF_UNIX):
            with self.assertRaises(OSError) as caught:socket.socket(family,socket.SOCK_STREAM)
            self.assertEqual(errno.EPERM,caught.exception.errno)
    def test_no_parallel_container_database_path(self):
        hits=[]
        for path in WORK.rglob('*.java'):
            if re.search(r'^import org\.testcontainers',path.read_text(),re.M):hits.append(str(path.relative_to(WORK)))
        self.assertEqual([],hits)
        for name in ('build.gradle.kts','media-module/build.gradle.kts'):
            self.assertNotIn('org.testcontainers:',(WORK/name).read_text())
        for name in ('scripts/local-test.sh','infra/scripts/local-test.sh','scripts/test/observe-connections.sh','scripts/test/observe-connections-state.sh','scripts/neon'):
            self.assertFalse((WORK/name).exists(),name)
    def test_migrated_business_bodies_match_reviewed_equivalence(self):
        data=json.loads((Path(__file__).parent/'contracts/migrated-test-bodies.json').read_text())
        self.assertEqual(6,len(data))
        self.assertEqual(30,sum(len(row['methods']) for row in data))
        self.assertEqual(91,sum(row['assertions'] for row in data))
        for row in data:self.assertEqual(row['methods'],test_bodies((WORK/row['path']).read_text()),row['path'])

if __name__=='__main__':unittest.main(verbosity=2)
