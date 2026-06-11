# Premium Offline Media Player 🎵🎬

A high-performance, robust, and beautiful offline-first Audio and Video playback application built with **Jetpack Compose**, **Kotlin**, and **Material Design 3 (M3)**. Engineered to deliver an immersive multimedia experience with sleek aesthetics, advanced playback controls, folder organization, and secure sign-off builds.

---

## 🚀 Key Features

### 1. Unified Multi-Tab Navigation
- **Audio Tab:** Browse local audio tracks organized by tracks, albums, favorites, artists, genres, or custom playlists.
- **Video Tab:** Scan and watch offline videos with support for widescreen scaling, custom orientations, and subtitle toggles.
- **Folders Tab:** Directory-by-directory tree browser showing physical media folders on device storage.

### 2. Premium Audio Player Screen
- **Glowing Card & Spectrum:** Responsive, large glowing album art with rich visuals and a state-of-the-art interactive wave spectrum visualizer.
- **Queue Overlay Sheet:** Bottom sheet playback queue management with responsive list items, swipe-to-remove, and direct selection.
- **Advanced Core Controls:** Custom playback speed (0.5x to 3.0x), suspension/sleep fade timers, and background audio focus listeners.
- **10-Band Equalizer Suite:** High-fidelity audio parameters with virtualizer, loudness maximizers, stereo balance sliders, and bass booster.

### 3. Smart Gestures & Controls (Video View)
- **Fluid Gesture Controls:** Swipe up and down on the left/right sides to adjust screen brightness and audio volume.
- **Aspect Ratio Adaptations:** Fit, Center Crop, Stretch, or Zoom to conform to device contours.

### 4. Advanced Preferences & Directory Exemptions
- **Top Tabs Manager:** Interactive list prioritizing custom orders for sub-tabs with status checkboxes.
- **Exempt Directories:** Interactive checklist to hide/unhide physical folder paths immediately from scanning.
- **Adaptive Visage Styling:** Toggleable dark/light and system-default appearance settings with persistent local cache.

---

## 🛠️ Architecture & Tech Stack

This application is built using modern Android development practices, ensuring high performance, clean structures, and strict security compliance:

- **Jetpack Compose (M3):** Uncompromising user experiences designed via complete reactive composability.
- **Room SQLite Database:** Ultra-fast, localized media-metadata caching and smart scanning to ensure seamless zero-lag boots.
- **Media3 Player Core (ExoPlayer):** Seamless background-foreground playback, rich audio focus handlings, and low battery consumption.
- **Kotlin Flow & Coroutines:** Thread-safe background multi-threaded directory crawlers.
- **Concurrent Album Art Cache:** Multi-level lookup engine reducing I/O friction on large track counts.

### Directory Structure

```text
/app/src/main/java/com/example
├── data/
│   ├── db/          # Room Entity models, DAOs, and Database
│   ├── model/       # Media items and playback queues
│   └── repository/  # Local persistence repositories
├── ui/
│   ├── components/  # Reusable widgets (widgets, cache, indicators)
│   ├── theme/       # Centralized Material Design 3 palettes
│   └── screens/     # Screens (MainScreen, AudioTab, FolderTab, Settings, Player) 
└── viewmodel/       # MediaViewModel state handlers
```

---

## 🔒 Security & Key Store Signing

Our build tooling features dynamic, secure code signing with standard protection and advanced configurations:

- **Custom Cryptographic Key (`sirajul.jks`):** Dynamically pre-baked and generated securely via cryptographic `keytool` configurations.
- **Security Parameters:**
  - **Key Alias:** `Sirajul`
  - **Organization:** `msi-dev`
  - **Owner (CN):** `msi.dev`
  - **Location:** KAPTAI, Chattogram, BD
- **Signature Integrity:** Fully supports **V1, V2, V3, and V4 signing models** for maximum safety from tampered installations on newer Android devices.

---

## 📦 Running & Obtaining Builds

Secure, pre-signed applications are compiled directly in the workspace root directory with dynamic version values (`v5.4` / Code `8`):

- **`/app-debug.apk`** — Complete development-tier signed binary.
- **`/app-release.apk`** — Production-grade signed distribution binary.

To trigger a rebuild and copy of these secure APK outputs at any point on your terminal, execute:
```bash
gradle copyApksToRoot
```

---

## 🎯 Developer Goals

1. **Craftsmanship in Code:** Strictly modular, robustly typed, zero redundant allocations, and structured following Clean Architectural guidelines.
2. **Offline-First Excellence:** No external metadata requests or telemetry leaks. Data, art, and control belong strictly to the user.
3. **Adaptive Dynamics:** Responsive scaling optimized for compactness on mobile-screens while remaining fully accessible across tablets and foldables.
