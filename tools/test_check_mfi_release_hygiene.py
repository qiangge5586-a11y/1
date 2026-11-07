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

    def test_original_offline_identity_assets_are_allowed(self):
        path = self.make_apk({
            "assets/offline-mfi/identity.pk8": b"original-pkcs8-fixture",
            "assets/offline-mfi/certificate.p7b": b"original-certificate-fixture",
        })
        self.assertEqual([], scan_apk(path))

    def test_separately_named_private_identity_exports_are_rejected(self):
        path = self.make_apk({"assets/mfi-private-key/export.bin": b"fixture"})
        self.assertTrue(any("forbidden identity path" in item for item in scan_apk(path)))

    def test_private_key_pem_payload_is_rejected(self):
        payload = (b"-----BEGIN PRIVATE KEY-----\n"
                   + b"A" * 128
                   + b"\n-----END PRIVATE KEY-----")
        path = self.make_apk({"assets/provider.dat": payload})
        self.assertTrue(any("private-key PEM payload" in item for item in scan_apk(path)))

    def test_pem_parser_delimiters_in_dex_are_allowed(self):
        parser = (b"-----BEGIN PRIVATE KEY-----"
                  + b" unrelated dex bytecode "
                  + b"-----END PRIVATE KEY-----")
        path = self.make_apk({"classes4.dex": parser})
        self.assertEqual([], scan_apk(path))


if __name__ == "__main__":
    unittest.main()
