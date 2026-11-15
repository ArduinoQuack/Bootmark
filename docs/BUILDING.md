# Building

Requires Linux x86_64, Python 3 and Java 21 with `java` and `keytool` on PATH.

```sh
python3 tools/setup_toolchain.py
python3 build_apk.py
```

The APK is written to `dist/Bootmark-1.2.apk`. The toolchain script downloads pinned Android SDK 36 tools and Eclipse ECJ from Google and Maven Central and verifies their SHA-256 hashes. No Gradle dependencies are needed for the app.

A local signing key is created in `build/local-signing` if none is supplied. An APK signed with this key cannot update the published release.

To use an existing key, set `BOOTMARK_KEYSTORE` to the PKCS12 file and `BOOTMARK_PASSWORD_FILE` to a file containing its password. The key alias is `bootmark`. Keep both files outside the repository.

`BOOTMARK_TOOLCHAIN` can point to another toolchain directory with the same layout.

## Tests

The Android 16 tests use Robolectric 4.16.1 and JUnit 4.13.2. Install Maven, run the toolchain setup and build, then:

```sh
python3 testtools/run_tests.py
```

These checks cover launch, theme changes, preset migration, previews and image/GIF imports. They run on the host and do not test a phone reboot or grant root.
