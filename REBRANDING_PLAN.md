# Miori Rebranding Master Plan & Status Tracker

This document is the persistent record for rebranding BitChord to **Miori** (`com.miori.music`). It tracks all completed steps, verified items, and remaining work so context is never lost across conversation summaries.

---

## 1. Scope & Core Directives
* **New App Name**: `Miori`
* **New Package Name**: `com.miori.music`
* **New Repo / Workspace**: `/data/data/com.termux/files/home/Miori`
* **Icon Aesthetic**: Custom vector foreground with **pure black** (`#000000`) background.
* **Preservation**: Original `/data/data/com.termux/files/home/BitChord` must remain 100% untouched.

---

## 2. Complete Phase Breakdown

### Phase 1: Workspace & Isolation
- [x] Create dedicated `Miori` directory from `BitChord` base.
- [x] Verify original `BitChord` directory remains intact and untracked.

### Phase 2: App Icon, Branding & Media Audit
- [x] Replace `ic_launcher_foreground.xml` with user-supplied vector.
- [x] Set `ic_launcher_background.xml` to pure black (`#FF000000`).
- [x] Update in-app logo `ic_logo.xml` in `app/src/main/res/drawable/`.
- [x] Update in-app logo `ic_logo.xml` in `sharedUi/src/commonMain/composeResources/drawable/`.
- [ ] Audit remaining 110 media/image files across repo for any embedded BitChord curved soundwave logo:
  - Check `notification_icon` / status bar icons.
  - Check web/desktop asset directories (`web/`, `desktop/`).
  - Check drawables for any legacy raster `.png`/`.webp` containing the old logo.
- [ ] Verify in-app procedural watermarks:
  - Replay card watermarks in `ReplayCard.kt`.
  - Lyrics share card watermark in `LyricsShareCard.kt`.

### Phase 3: User-Facing Text & Translations
- [x] Replace "BitChord" with "Miori" across all 21 `strings.xml` translation files.
- [x] Update `app_name` string across all language locales.
- [ ] Verify no hardcoded "BitChord" display titles remain in Compose UI screens.

### Phase 4: Package Architecture & Code Renaming
- [x] Migrate all 16 package roots from `com/music/bitchord` to `com/miori/music`:
  - `app/src/main/java/com/miori/music`
  - `app/src/androidTest/java/com/miori/music`
  - `app/src/debug/java/com/miori/music`
  - `app/src/test/java/com/miori/music`
  - `shared/src/commonMain/kotlin/com/miori/music`
  - `shared/src/commonTest/kotlin/com/miori/music`
  - `shared/src/jvmSharedMain/kotlin/com/miori/music`
  - `shared/src/jvmTest/kotlin/com/miori/music`
  - `sharedUi/src/androidMain/kotlin/com/miori/music`
  - `sharedUi/src/jvmMain/kotlin/com/miori/music`
  - `sharedUi/src/jvmSharedMain/kotlin/com/miori/music`
  - `sharedUi/src/jvmTest/kotlin/com/miori/music`
  - `tv/src/main/java/com/miori/music`
  - `tv/src/test/java/com/miori/music`
  - `wearos/protocol/com/miori/music`
  - `wearos/src/main/java/com/miori/music`
- [x] Rename `BitChordApplication.kt` ➔ `MioriApplication.kt`.
- [x] Rename `BitChordIcons.kt` ➔ `MioriIcons.kt`.
- [x] Update all Kotlin `package` declarations and `import` statements.
- [x] Update backend deployment scripts (`miori-deploy.sh`, `miori-jam.service`).

### Phase 5: Build System, Manifests & Native Code
- [x] Set `applicationId = "com.miori.music"` and `namespace = "com.miori.music"` in `app/build.gradle.kts`.
- [x] Set `rootProject.name = "Miori"` in `settings.gradle.kts`.
- [x] Update version variables (`mioriVersionCode`, `mioriVersionName`, `mioriBetaSuffix`).
- [x] Update `UPDATE_APK_PREFIX = "Miori-"` in build config.
- [x] Update all `AndroidManifest.xml` files (`app`, `tv`, `wearos`, `dev`) with `MioriApplication` and `Theme.Miori`.
- [x] Update `CMakeLists.txt` and C++ native code definitions (`MIORI_NATIVE_DIR`).

### Phase 6: Links, Server Addresses, Comments & Documentation
- [x] Global text replacement in 861 non-binary files.
- [ ] Audit external links (GitHub repo URLs, documentation, README).
- [ ] Audit server endpoints:
  - Listen Together party server default address.
  - Addon authentication URL fallback.

### Phase 7: Verification & Build Readiness
- [x] Clean local git commit (`6e2f303`) tracking all renames and updates.
- [ ] Gradle configuration check to verify 0 syntax or dependency issues.
- [ ] Setup GitHub remote repository & Actions CI build pipeline.
