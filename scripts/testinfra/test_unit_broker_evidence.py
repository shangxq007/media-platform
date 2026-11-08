"""Real kernel flock and descriptor checks, without granting Broker admission."""
import os
from pathlib import Path
import tempfile
import unittest

from broker_evidence import read_control_journal
from state import Journal,PolicyError


class BrokerEvidenceTests(unittest.TestCase):
    def test_named_ledger_and_exact_peer_lock_are_required(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'ledger';lock=Path(folder)/'lock'
            directory=os.open(folder,os.O_RDONLY|os.O_DIRECTORY)
            journal=Journal(path,lock).acquire()
            try:
                journal.initialize(True)
                fd=os.open(path,os.O_RDONLY|os.O_NOFOLLOW)
                try:
                    def read(pid=os.getpid(),uid=os.getuid(),ledger_path=path,lock_path=lock):
                        return read_control_journal(fd,journal._lock,pid,uid,ledger_path,lock_path,directory)
                    self.assertEqual('NEW',read()['state'])
                    for kwargs in ({'pid':os.getpid()+999999},{'uid':os.getuid()+1},
                                   {'ledger_path':path.with_name('other')},{'lock_path':lock.with_name('other')}):
                        with self.subTest(kwargs=kwargs),self.assertRaises(PolicyError):read(**kwargs)
                    path.rename(path.with_name('old'))
                    with self.assertRaises(PolicyError):read()
                finally:os.close(fd)
            finally:journal.release();os.close(directory)

    def test_unlocked_and_writable_descriptors_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'ledger';lock=Path(folder)/'lock'
            directory=os.open(folder,os.O_RDONLY|os.O_DIRECTORY)
            journal=Journal(path,lock).acquire();journal.initialize(True)
            writable=os.open(path,os.O_RDWR|os.O_NOFOLLOW)
            try:
                with self.assertRaises(PolicyError):
                    read_control_journal(writable,journal._lock,os.getpid(),os.getuid(),path,lock,directory)
            finally:os.close(writable);journal.release()
            fd=os.open(path,os.O_RDONLY);lock_fd=os.open(lock,os.O_RDWR)
            try:
                with self.assertRaises(PolicyError):
                    read_control_journal(fd,lock_fd,os.getpid(),os.getuid(),path,lock,directory)
            finally:os.close(fd);os.close(lock_fd);os.close(directory)


if __name__=='__main__':unittest.main()
