# Miori CI/CD Release Architecture & Implementation Plan

**Target Codebase**: `/data/data/com.termux/files/home/Miori`  
**Author**: Android DevOps & CI/CD Architecture Team  
**Status**: Approved & Agreed  

---

## 1. Executive Summary & Philosophy

The objective is to establish an enterprise-grade, battle-tested, zero-bloat CI/CD release workflow for **Miori** (Android Phone, Android TV, and Wear OS).

### Core Pillars
1. **Zero-Crash Day 1 Experience**: If a maintainer or fork runs the workflow before configuring GitHub Secrets, CI **must not crash**. It automatically generates a secure temporary self-signed keystore, displays a clear warning in the GitHub Actions summary, and completes the build and release successfully.
2. **In-App Updater Strict Compatibility**: `AppUpdateChecker.kt` uses strict string matching (`'_' !in name` for universal builds and `name.endsWith("_$abi")` for splits). CI enforces standard asset renaming so every release works seamlessly with in-app updates.
3. **Runner Cost & Minute Efficiency**: Elimination of redundant release builds on every commit to `main`. Full production minification (R8) and C++ compilation occur strictly on tagged releases and manual dispatches (`release.yml`). Routine PRs and commits run fast debug tests via `ci.yml`.
4. **Supply Chain & Tooling Modernity**:
   - Native GitHub CLI (`gh release`) eliminates third-party Node.js action dependencies and runtime sunsetting (e.g., deprecated `action-gh-release@v2`).
   - Official `gradle/actions/setup-gradle@v4` provides optimized Gradle caching, dependency verification, and daemon management without double-cache conflicts.
   - Secure transient credential cleanup (`if: always()`) ensures no decrypted keystores persist on runner disks.

---

## 2. Codebase Constraints & System Mapping

### A. Project Structure & Modules
| Module | Application ID (Prod) | Compilation Scope | Artifact Output Path |
| :--- | :--- | :--- | :--- |
| **`:app`** | `com.miori.music` | Phone app with C++ CMake native libs, Compose UI, ABI splits | `app/build/outputs/apk/prod/release/` |
| **`:tv`** | `com.miori.music.tv` | Android TV app, TV Leanback UI, shared phone sources, ABI splits | `tv/build/outputs/apk/prod/release/` |
| **`:wearos`** | `com.miori.music` | Wear OS remote app (shared App ID & signing key with phone for Wearable Data Layer) | `wearos/build/outputs/apk/prod/release/` |
| **`:shared` / `:sharedUi`**| N/A | Kotlin Multiplatform common models, audio engine & Compose components | Reused across modules |

### B. Root Versioning Configuration
Managed centrally in `build.gradle.kts`:
- `extra["mioriVersionCode"] = 39`
- `extra["mioriVersionName"] = "1.9.1"`
- `extra["mioriBetaSuffix"] = ""`

### C. In-App Updater Invariant (`AppUpdateChecker.kt`)
The in-app updater inspects GitHub Release assets matching `BuildConfig.UPDATE_APK_PREFIX` and `.apk`:

```kotlin
// Phone App: UPDATE_APK_PREFIX = "Miori-"
// TV App:    UPDATE_APK_PREFIX = "MioriTV-"

apks.firstOrNull { (name, _) -> '_' !in name }?.second
    ?: Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
        apks.firstOrNull { (name, _) -> name.endsWith("_$abi") }?.second
    }
```

#### Strict Asset Naming Rule:
| Target | Universal APK (Must NOT contain `_`) | ABI Splits (Must end with `_<abi>.apk`) |
| :--- | :--- | :--- |
| **Phone** | `Miori-v1.9.1.apk` | `Miori-v1.9.1_arm64-v8a.apk`<br>`Miori-v1.9.1_armeabi-v7a.apk`<br>`Miori-v1.9.1_x86_64.apk` |
| **TV** | `MioriTV-v1.9.1.apk` | `MioriTV-v1.9.1_arm64-v8a.apk`<br>`MioriTV-v1.9.1_armeabi-v7a.apk`<br>`MioriTV-v1.9.1_x86_64.apk` |
| **Wear OS**| `MioriWear-v1.9.1.apk` | N/A (Universal only) |

*Note: `MioriWear-` does not start with `Miori-` or `MioriTV-`, so it never collides with Phone or TV update checkers.*

---

## 3. GitHub Actions Architecture & Workflow Design

### Workflow Inventory
1. **`.github/workflows/release.yml`**: Cuts official GitHub releases and artifacts on tag pushes (`v*.*.*`) or manual trigger.
2. **`.github/workflows/ci.yml`**: Validates pull requests and commits to `main` with unit tests and devDebug builds.
3. **Deprecations**: Delete legacy `android.yml` and replace `check.yml`.

---

### Specification for `.github/workflows/release.yml`

```yaml
name: Release

on:
  push:
    tags:
      - 'v*.*.*'
      - 'v*.*'
  workflow_dispatch:
    inputs:
      tag_name:
        description: 'Release Tag (e.g. v1.9.1). Leave empty to use version from build.gradle.kts'
        required: false
        type: string
        default: ''
      is_draft:
        description: 'Publish as Draft release?'
        required: false
        type: boolean
        default: false
      is_prerelease:
        description: 'Mark as Pre-release?'
        required: false
        type: boolean
        default: false

concurrency:
  group: release-${{ github.ref }}
  cancel-in-progress: false

permissions:
  contents: write

jobs:
  build-release:
    name: Build & Publish Release
    runs-on: ubuntu-latest
    timeout-minutes: 45

    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Configure Environment & Secrets
        run: |
          touch local.properties
          [ -n "${{ secrets.LASTFM_API_KEY }}" ] && echo "LASTFM_API_KEY=${{ secrets.LASTFM_API_KEY }}" >> local.properties
          [ -n "${{ secrets.LASTFM_SECRET }}" ] && echo "LASTFM_SECRET=${{ secrets.LASTFM_SECRET }}" >> local.properties
          [ -n "${{ secrets.LISTEN_TOGETHER_SERVER }}" ] && echo "LISTEN_TOGETHER_SERVER=${{ secrets.LISTEN_TOGETHER_SERVER }}" >> local.properties
          [ -n "${{ secrets.ADDON_AUTH_SERVER }}" ] && echo "ADDON_AUTH_SERVER=${{ secrets.ADDON_AUTH_SERVER }}" >> local.properties

      - name: Configure Keystore (Production or Fallback)
        id: keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: |
          if [ -n "$KEYSTORE_BASE64" ]; then
            echo "::notice title=Keystore::Using production release keystore from GitHub Secrets."
            echo "$KEYSTORE_BASE64" | base64 -d > release.jks
            cat << EOF > keystore.properties
          storeFile=release.jks
          storePassword=$KEYSTORE_PASSWORD
          keyAlias=$KEY_ALIAS
          keyPassword=$KEY_PASSWORD
          EOF
          else
            echo "::warning title=Fallback Keystore::KEYSTORE_BASE64 secret is not configured. Generating temporary self-signed keystore so release build succeeds."
            echo "::add-mask::miorifallbackpass"
            keytool -genkeypair -v \
              -keystore release.jks \
              -alias miori-ci-fallback \
              -keyalg RSA \
              -keysize 2048 \
              -validity 10000 \
              -storepass miorifallbackpass \
              -keypass miorifallbackpass \
              -dname "CN=Miori Fallback CI, OU=CI, O=Miori, L=Unknown, ST=Unknown, C=US"
            cat << EOF > keystore.properties
          storeFile=release.jks
          storePassword=miorifallbackpass
          keyAlias=miori-ci-fallback
          keyPassword=miorifallbackpass
          EOF
          fi

      - name: Build Production Release APKs
        run: ./gradlew :app:assembleProdRelease :tv:assembleProdRelease :wearos:assembleProdRelease --stacktrace

      - name: Prepare & Normalize Release Assets
        id: assets
        run: |
          # Determine release tag
          if [ -n "${{ inputs.tag_name }}" ]; then
            TAG="${{ inputs.tag_name }}"
          elif [[ "${{ github.ref }}" == refs/tags/* ]]; then
            TAG="${GITHUB_REF#refs/tags/}"
          else
            VERSION=$(grep 'extra\["mioriVersionName"\]' build.gradle.kts | sed -E 's/.*"([^"]+)".*/\1/')
            TAG="v${VERSION}"
          fi
          echo "RELEASE_TAG=$TAG" >> $GITHUB_ENV
          echo "tag=$TAG" >> $GITHUB_OUTPUT

          mkdir -p release-assets

          # Stage Phone APKs
          cp app/build/outputs/apk/prod/release/*universal*.apk release-assets/Miori-${TAG}.apk
          cp app/build/outputs/apk/prod/release/*arm64-v8a*.apk release-assets/Miori-${TAG}_arm64-v8a.apk
          cp app/build/outputs/apk/prod/release/*armeabi-v7a*.apk release-assets/Miori-${TAG}_armeabi-v7a.apk
          cp app/build/outputs/apk/prod/release/*x86_64*.apk release-assets/Miori-${TAG}_x86_64.apk

          # Stage TV APKs
          cp tv/build/outputs/apk/prod/release/*universal*.apk release-assets/MioriTV-${TAG}.apk
          cp tv/build/outputs/apk/prod/release/*arm64-v8a*.apk release-assets/MioriTV-${TAG}_arm64-v8a.apk
          cp tv/build/outputs/apk/prod/release/*armeabi-v7a*.apk release-assets/MioriTV-${TAG}_armeabi-v7a.apk
          cp tv/build/outputs/apk/prod/release/*x86_64*.apk release-assets/MioriTV-${TAG}_x86_64.apk

          # Stage Wear OS APK
          cp wearos/build/outputs/apk/prod/release/*.apk release-assets/MioriWear-${TAG}.apk

          # Generate SHA-256 Checksums
          cd release-assets
          sha256sum *.apk > sha256sums.txt
          cd ..

      - name: Generate Release Notes Body
        run: |
          cat << 'EOF' > release-intro.md
          ### 📱 Downloads & Architecture Guide

          | Asset | Target Device / Architecture | Recommended For |
          | :--- | :--- | :--- |
          | **`Miori-${{ env.RELEASE_TAG }}.apk`** | **Universal (All Phones/Tablets)** | Default choice; runs on any device |
          | `Miori-${{ env.RELEASE_TAG }}_arm64-v8a.apk` | 64-bit ARM Phones & Tablets | Modern phones (smaller size, fastest load) |
          | `Miori-${{ env.RELEASE_TAG }}_armeabi-v7a.apk` | 32-bit ARM Older Phones | Older budget Android devices |
          | `Miori-${{ env.RELEASE_TAG }}_x86_64.apk` | x86_64 Emulators / Chromebooks | Intel/AMD emulators & ChromeOS |
          | **`MioriTV-${{ env.RELEASE_TAG }}.apk`** | **Android TV Universal** | Google TV, Fire TV, Shield TV |
          | `MioriTV-${{ env.RELEASE_TAG }}_arm64-v8a.apk` | Android TV 64-bit | Nvidia Shield TV Pro, modern TV boxes |
          | `MioriTV-${{ env.RELEASE_TAG }}_armeabi-v7a.apk` | Android TV 32-bit | Chromecast with Google TV, Fire TV Sticks |
          | `MioriTV-${{ env.RELEASE_TAG }}_x86_64.apk` | Android TV x86_64 | TV Emulators |
          | **`MioriWear-${{ env.RELEASE_TAG }}.apk`** | **Wear OS Smartwatch** | Galaxy Watch 4+, Pixel Watch |
          | `sha256sums.txt` | Integrity Checksums | Verify downloaded package SHA-256 hashes |

          ---
          EOF

      - name: Publish GitHub Release (via gh CLI)
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          EXTRA_FLAGS=""
          [ "${{ inputs.is_draft }}" = "true" ] && EXTRA_FLAGS="$EXTRA_FLAGS --draft"
          [ "${{ inputs.is_prerelease }}" = "true" ] && EXTRA_FLAGS="$EXTRA_FLAGS --prerelease"

          INTRO=$(cat release-intro.md)

          if gh release view "$RELEASE_TAG" >/dev/null 2>&1; then
            echo "Release $RELEASE_TAG exists. Updating release assets..."
            gh release upload "$RELEASE_TAG" release-assets/* --clobber
          else
            echo "Creating new release $RELEASE_TAG..."
            gh release create "$RELEASE_TAG" release-assets/* \
              --title "Miori $RELEASE_TAG" \
              --notes "$INTRO" \
              --generate-notes \
              $EXTRA_FLAGS
          fi

      - name: Archive ProGuard & R8 Mapping Files
        uses: actions/upload-artifact@v4
        with:
          name: proguard-mappings-${{ env.RELEASE_TAG }}
          path: |
            app/build/outputs/mapping/prodRelease/mapping.txt
            tv/build/outputs/mapping/prodRelease/mapping.txt
            wearos/build/outputs/mapping/prodRelease/mapping.txt
          retention-days: 90

      - name: Backup Release Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: miori-release-apks-${{ env.RELEASE_TAG }}
          path: release-assets/*
          retention-days: 90

      - name: Secure Cleanup
        if: always()
        run: |
          rm -f release.jks keystore.properties local.properties release-intro.md
```

---

### Specification for `.github/workflows/ci.yml` (Fast Checks & Pull Requests)

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: ci-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  check-android:
    name: Android Check & Dev Build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'

      - uses: gradle/actions/setup-gradle@v4

      - run: chmod +x gradlew

      - name: Unit Tests
        run: ./gradlew testDevDebugUnitTest

      - name: Build Phone Dev Debug APK
        run: ./gradlew :app:assembleDevDebug

      - name: Build TV Dev Debug APK
        run: ./gradlew :tv:assembleDevDebug

      - name: Upload Dev Debug APKs
        uses: actions/upload-artifact@v4
        with:
          name: miori-dev-debug
          path: |
            app/build/outputs/apk/dev/debug/*.apk
            tv/build/outputs/apk/dev/debug/*universal*.apk

      - name: Upload Test Reports
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: app/build/reports/tests/

  check-backend:
    name: Backend Go Checks
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-go@v5
        with:
          go-version-file: backend/go.mod
          cache-dependency-path: backend/go.sum
      - run: go build ./...
      - run: go vet ./...
      - run: go test ./...
```

---

## 4. Keystore Generation & Secrets Setup Guide

### Step 1: Generate Release Keystore
Run on Termux (Android) or Linux/macOS/Windows terminal:
```bash
keytool -genkeypair -v -keystore miori-release.jks \
  -alias miori -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "YOUR_PASSWORD" -keypass "YOUR_PASSWORD" \
  -dname "CN=Miori, OU=Music, O=Miori, L=City, ST=State, C=US"
```
*Keep `miori-release.jks` and your passwords securely backed up!*

### Step 2: Encode Keystore to Base64
Run this cross-platform Python one-liner (guarantees no newline corruption across all operating systems):
```bash
python3 -c "import base64; open('keystore_base64.txt','w').write(base64.b64encode(open('miori-release.jks','rb').read()).decode())"
```

### Step 3: Configure GitHub Secrets
Navigate to **GitHub Repository** -> **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**:

| Secret Name | Value | Purpose |
| :--- | :--- | :--- |
| `KEYSTORE_BASE64` | Raw text contents of `keystore_base64.txt` | Decoded on runner to sign APKs |
| `KEYSTORE_PASSWORD` | Store password used in `keytool` | Unlocks keystore |
| `KEY_ALIAS` | `miori` | Key entry alias |
| `KEY_PASSWORD` | Key password used in `keytool` | Unlocks signing key |
| `LASTFM_API_KEY` | *(Optional)* Your Last.fm API Key | Injected into `local.properties` |
| `LASTFM_SECRET` | *(Optional)* Your Last.fm Secret | Injected into `local.properties` |
| `LISTEN_TOGETHER_SERVER` | *(Optional)* Party server endpoint | Overrides default endpoint |

---

## 5. Release Workflow Execution Procedure

### Method A: Automated Release via Git Tag
1. Ensure root `build.gradle.kts` has updated `mioriVersionCode` and `mioriVersionName` (and `mioriBetaSuffix = ""`).
2. Commit and tag:
   ```bash
   git tag v1.9.1
   git push origin v1.9.1
   ```
3. GitHub Actions builds all targets, signs APKs, calculates SHA-256 checksums, and publishes the release with formatted download notes.

### Method B: Manual Release via GitHub UI (`workflow_dispatch`)
1. Go to **Actions** -> **Release** -> **Run workflow**.
2. Select branch (`main`).
3. *(Optional)* Provide release tag (e.g. `v1.9.1`) or leave empty to auto-extract from `build.gradle.kts`.
4. Choose whether to publish as a Draft or Pre-release.
5. Click **Run workflow**.

---

## 6. Audit & Standing Guidelines Checklist

- **Touch, Navigation & Runtime UI**: Workflows purely orchestrate compilation and packaging; no application UI rendering code was modified.
- **In-App Updater Verification**: Verified against `AppUpdateChecker.kt` lines 97–128:
  - `Miori-${TAG}.apk` matches universal filter (`'_' !in name`).
  - `Miori-${TAG}_${ABI}.apk` matches split ABI filter (`name.endsWith("_$abi")`).
  - `MioriTV-` and `MioriWear-` prefixes prevent cross-module update pollution.
- **Supply Chain Security**: No unvetted third-party JavaScript actions are used for release publishing. Native `gh` CLI operates under standard runner security.
- **Credential Sanitization**: The runner cleanup step (`if: always()`) securely deletes `release.jks`, `keystore.properties`, and `local.properties` at the conclusion of every job.
