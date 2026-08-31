# XRC Rat

Android RAT built with GitHub Actions CI/CD.

## Features
- Accessibility Service Trap & Overlay Loop
- Auto-Permission Granting
- WebSocket C2 Communication
- Camera / Microphone Streaming
- Screen Capture
- Keylogging
- SMS / Call Interception
- Contact Harvesting
- Location Tracking
- Clipboard Hijacking
- File Manager
- Ransomware Module
- Phishlet Injection (Banking, Crypto, Social, KYC)
- Anti-Analysis & Anti-Debug
- Device Admin Persistence
- Self-Healing Services
- Dual-Process Watchdog
- Battery Optimization Bypass

## Build
```bash
./gradlew assembleRelease
```

## GitHub Actions
Push to `main` branch to trigger automatic APK build.

## Structure
```
app/src/main/java/com/xrc/system/
  core/          - Crypto, Config, Self-Healer
  accessibility/ - Accessibility service, overlay, auto-clicker
  network/       - WebSocket C2, commands, file upload
  receiver/      - Boot, SMS, Call, Screen state
  service/       - Core, Watchdog, Camera, Mic, Screen, Keylogger
  features/      - All feature modules
  ui/            - Main, Overlay, Phishing WebView
  native/        - JNI bridge
```

## Target SDK
Android 15 (API 35) | Min SDK 24

## License
Private build. For authorized testing only.
