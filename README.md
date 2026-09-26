# Clean Phone - Your Phone 🧹⚡

A lightweight, zero-overhead Android utility for automated file organization, deep cache cleaning, and instant memory boosting with 0% battery drain.

---

## 📲 Download APK

Get the latest pre-compiled and signed APK:
- 🚀 **[Download Clean_Phone_Your_Phone.apk](https://github.com/AARESLY/Clean-Phone-Your-Phone/releases/download/v1.0.0/Clean_Phone_Your_Phone.apk)** (Direct Download)
- 📦 View all releases on the **[Releases Page](https://github.com/AARESLY/Clean-Phone-Your-Phone/releases)**

---

## 🌟 Highlights

- **🗂️ Zero-Battery File Organizer**: Uses Linux kernel `inotify` triggers to sort incoming downloads into organized categories (`Images`, `Documents`, `Archives`, `Code_and_Notes`, `Audio`, `Videos`) only when files arrive. No polling loops.
- **📂 One-Tap Disorganizer**: Instantly restore categorized files back to root anytime.
- **🧹 Deep Cache Cleaner**: 
  - **Root**: Wipes app and system cache instantly in a single command.
  - **Non-Root**: Automated Accessibility scanner cleans caches without manual taps.
- **🛑 App Closer (Free RAM)**: Force-closes background apps to reclaim system memory immediately.
- **🌗 Dark & Light Themes**: Beautiful high-contrast UI tailored for both modes.
- **📊 Real-time Dashboard**: Live RAM, storage monitors, and activity event log.

---

## 🏗️ Structure

Built with pure native Android SDK tools (`aapt2`, `d8`, `javac`) — zero third-party dependencies:

```
├── AndroidManifest.xml                  # App manifest & service definitions
├── build_apk.sh                         # Standalone build & signing script
├── res/                                 # Layouts, adaptive theme vectors & styles
└── src/com/organizer/downloads/         # Core logic: inotify service, cleaner, root helper
```

---

## 🚀 Build from Source

```bash
chmod +x build_apk.sh
./build_apk.sh
```
The signed APK outputs to `build/DownloadOrganizer.apk`.

---

## 🔒 Privacy & Permissions
- **Storage & Accessibility (Optional)**: Used strictly for local file sorting and automated cleaning.
- **100% Offline**: Zero analytics, zero telemetry, zero data collected.

---

## 📄 License
Licensed under the [MIT License](LICENSE).
