# NiagaraHome

## Build & Install

- Build: `./gradlew assembleDebug`
- Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- Do NOT use `./gradlew installDebug` — adb bridge fails in Termux
