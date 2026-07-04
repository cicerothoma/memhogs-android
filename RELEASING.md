# Releasing memhogs

## Signing credentials (never commit these)

Release builds are signed with a keystore that lives OUTSIDE this repo:

- Keystore: `~/keystores/memhogs-release.jks`
- Credentials: `~/keystores/memhogs-release-credentials.txt`, mirrored in
  `keystore.properties` at the repo root

`keystore.properties` and `*.jks` are gitignored. Keep it that way: the
keystore file and its passwords must never be committed, pasted into an
issue, or uploaded anywhere. Every sideloaded install is bound to this
signature permanently; if the key leaks, an attacker can ship malicious
"updates", and if it is lost, existing users cannot upgrade without
uninstalling. Back both files up somewhere private (password manager or
encrypted drive).

Without `keystore.properties`, release builds are simply unsigned. That
is intentional: CI and F-Droid build and sign with their own keys.

## Cutting a release

1. Bump `versionCode` (integer, always +1) and `versionName` in
   `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   with a short changelog (F-Droid shows it on the update screen).
3. Build and sanity-check:

   ```sh
   ./gradlew test assembleRelease
   ```

4. Tag and publish:

   ```sh
   git tag vX.Y.Z && git push origin vX.Y.Z
   cp app/build/outputs/apk/release/app-release.apk memhogs-X.Y.Z.apk
   gh release create vX.Y.Z memhogs-X.Y.Z.apk --title "memhogs X.Y.Z" --notes "..."
   ```

F-Droid picks new versions up automatically from the `vX.Y.Z` tag
(`AutoUpdateMode: Version` in its metadata), so after the first
inclusion there is nothing more to do per release.

## F-Droid

The recipe F-Droid builds from is `fdroid/dev.collinsthomas.memhogs.yml`
in this repo (a copy for reference; the live one is in fdroiddata). App
store text and screenshots come from `fastlane/metadata/android/en-US/`.

First-time submission:

1. Create a GitLab account if needed, fork
   https://gitlab.com/fdroid/fdroiddata
2. Copy `fdroid/dev.collinsthomas.memhogs.yml` into the fork as
   `metadata/dev.collinsthomas.memhogs.yml`
3. Open a merge request titled "New app: memhogs". The template
   checklist asks to confirm the app is FOSS (MIT, no proprietary
   dependencies; Shizuku API is Apache-2.0) and builds from source.
4. Reviewers may take a few weeks. Expect a question about
   QUERY_ALL_PACKAGES; the answer is that the app lists every process's
   owner by design, and the permission only resolves package names to
   labels and icons.
