import os
import tempfile
import unittest
import zipfile
import runpy

scan_apk = runpy.run_path(os.path.join(os.path.dirname(__file__),
                                      'check-mfi-release-hygiene.py'))['scan_apk']


class MfiReleaseHygieneTest(unittest.TestCase):
    def make_apk(self, entries):
        handle, path = tempfile.mkstemp(suffix=".apk")
        os.close(handle)
        with zipfile.ZipFile(path, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)
        self.addCleanup(lambda: os.path.exists(path) and os.unlink(path))
        return path

    def test_clean_archive_passes(self):
        path = self.make_apk({"classes.dex": b"ordinary application bytecode"})
        self.assertEqual([], scan_apk(path))

    def test_offline_identity_path_is_rejected(self):
        path = self.make_apk({"assets/offline-mfi/certificate.bin": b"fixture"})
        self.assertTrue(any("offline-mfi" in item for item in scan_apk(path)))

    def test_private_key_pem_is_rejected(self):
        path = self.make_apk({"assets/provider.dat": b"-----BEGIN PRIVATE KEY-----"})
        self.assertTrue(any("private-key" in item for item in scan_apk(path)))


if __name__ == "__main__":
    unittest.main()
