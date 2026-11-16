# Aura Charge

A small, black-background charging companion for Android 8.0+ (API 26). The interface defaults to Turkish; English and Persian are selectable. Persian and Arabic are shaped by Android and the bundled Vazirmatn font. The feed can show the source text, Turkish, or both. Add, edit, delete, reorder, and import your own UTF-8 `.txt` texts, alone or mixed with the library.

## Architecture

A single Compose activity hosts settings and the charging screen. `ChargeViewModel` owns preferences, custom texts and the local library, exposing changes through a `StateFlow`; a battery broadcast supplies live state. `PowerReceiver` posts a notification rather than illegally starting an activity in the background. A user-enabled foreground service keeps a dynamic receiver active; there is also a manifest receiver. A `MediaPlayer` handles one loop and audio focus. The Canvas renderer uses fewer particles and roughly 20 fps on API <29 or low-RAM devices and targets roughly 60 fps elsewhere. Animations and playback stop when the screen is off or the activity is not visible. Android's animator-duration-zero setting disables moving text and the connection sequence.

The connection phase lasts about three seconds, followed by a small bottom battery ring and a rising, scaling text feed. Select any combination of categories in settings; show the list and tap a text to start there, or enable shuffle for a randomized order. Otherwise the next session continues sequentially. The visible Next button advances immediately; entries also advance automatically at the selected speed. Long-press, swipe down, or use Close. Preview works without a charger. Battery readings are device estimates: current may be unavailable or have device-specific sign, watts are battery voltage × absolute current (not charger input power), and fast charging is a heuristic at 1,500 mA. Time remaining is available only when Android supplies it. The 1–2 pixel dock shift is cosmetic, not a guarantee against burn-in.

Music defaults on for new settings. Category music alternates the owner's two poetry, story, or religious tracks as entries advance; educational verses use the religious pair, wisdom uses the story pair, and custom text uses the poetry pair. Select any one bundled track, one of six generated loops, or a file using the document picker as a global override. Existing saved track choices are preserved. Test music previews the first poetry track in category mode. Also raise the phone's **media** volume. The optional silent-mode restriction prevents playback while the phone is silent or vibrating. Audio plays while the charging screen or test is visible, not from the monitoring service or while the screen is off. Playback requires audio focus and may stop on interruption. The app takes persistable read permission for a selected file; if the provider revokes it, select the file again.

### Platform limitations

- Android 10–14 restrict background activity launches and full-screen intents. The full-screen intent is a best-effort request, not a guarantee; Android 14+ may reserve that privilege for calls and alarms. Tap the charging notification if the screen does not open. Enable notifications on Android 13+, disable battery optimizations for the app only if needed, and check Samsung/Motorola background restrictions. Notification and service channels can be controlled in Android settings. The user-enabled service is not automatically restarted after reboot or force-stop; reopen the app and toggle monitoring if necessary. Lock-screen-only applies to background alerts, not an explicitly opened app or preview.
- No overlay permission, accessibility service, wakelock, or automatic unlock is used. Lock-screen visibility is subject to the device's security and OEM behavior.
- The renderer uses Canvas on all API levels; the optional AGSL effect is **not implemented**. Ring, Aurora and Lightning vary the connection-filament appearance rather than being three fully distinct animations. The dock transition is a spring, not a shared-element transition. A small bolt and optional haptic tick mark the lock-in.
- Screen-off and reduced-motion handling are implemented but have not been validated on the specified physical phones. Some long passages may need smaller text on short displays. Audio focus is respected; an interruption or a switch to silent mode stops playback rather than resuming automatically. Imported audio is accessed through a persisted Android document URI; if the provider revokes access it must be selected again.
- The library is intentionally limited to checked classical excerpts, not an exhaustive anthology. There are no Nahj al-Balagha or Ansari selections yet; uncertain material and modern work without a clear public-domain status were omitted rather than invented.

## Content and credits

The built-in library has **258 entries**: 40 Persian poetry excerpts, 40 Saadi hikâye selections, 40 religious selections (28 hadith excerpts from Sahih al-Bukhari and 12 Quran meanings), 40 educational Quran verses with Arabic text and Turkish takeaways, 55 Saadi prose/wisdom selections, and 43 other Persian Quran meaning renderings. The numbered Bukhari Arabic excerpts were selected from [Hadith API](https://github.com/fawazahmed0/hadith-api); they are excerpts, not full narrations. The working translations and attribution still require qualified review. The educational verses use the Uthmani text from [Al Quran Cloud](https://alquran.cloud/); their Turkish meanings and lessons are working renderings requiring expert review. Persian literary excerpts were checked against editions on [Ganjoor](https://ganjoor.net/), including [Hafez](https://ganjoor.net/hafez/ghazal/sh1), [Rumi](https://ganjoor.net/moulavi/masnavi/daftar1/sh1), [Saadi stories](https://ganjoor.net/saadi/golestan/gbab1/sh1), [Saadi sayings](https://ganjoor.net/saadi/golestan/gbab8/sh1), and [Attar](https://ganjoor.net/attar/manteghotteyr/aghazm/sh1). Textual variants occur across editions. Source data and references are in `scripts/library.tsv` (pipe-separated UTF-8, despite the filename); run `python3 scripts/build_library.py` after editing.

The Turkish translations and the Persian Quran meaning renderings are original **machine-assisted working translations**, not published scholarly translations. They need review by qualified Persian/Turkish editors and, for scripture, a qualified religious scholar before relying on them or distributing them as authoritative. Verse excerpts can lose context. The original classical works are public domain; no modern copyrighted translation is intentionally included. Public-domain rules can vary by jurisdiction.

Vazirmatn is by Rastikerdar and contributors, under the SIL Open Font License 1.1; the license is in `licenses/OFL-Vazirmatn.txt`. The system serif selection uses device fonts. `ambient1.wav` through `ambient6.wav` are original procedural recordings generated for this project and dedicated to the public domain under CC0 1.0. They are mono 22.05 kHz WAV files, each under 3 MB, synthesized with periodic drones and circular echoes. Regenerate with `python3 scripts/generate_audio.py` (requires NumPy); check amplitude and size with `python3 scripts/check_audio.py`. They are stylized ambient textures, not recordings of traditional instruments or a scholarly rendition of a dastgah. Your own audio remains subject to its own license.

The six user-supplied MP3 files are included for the owner's personal use; the repository's code license does not grant rights to redistribute those recordings.

## Installation and screenshots

Download the APK from GitHub Releases and install it on your phone, allowing installation from your browser/files app when prompted. Grant notification permission and enable monitoring in settings for background alerts. Choose Preview to test without charging. Screenshot placeholders: charging screen • settings • Persian text. The APK from a release without signing secrets is **debug-signed** and intended for testing; debug keys generated on different runners will not be compatible for in-place updates.

The locally built 1.0.1 debug APK uses a different debug key from the supplied local 1.0.0 APK. Android cannot update one with the other: uninstalling the old app first removes its settings and personal texts. Save those texts separately before uninstalling, or rebuild with the original signing key if it is available.

The local 1.0.2 debug APK was built with the retained 1.0.1 debug key, whose certificate SHA-256 is `8e6397c92a86bcc95e69f4a51ef4bf0f1ec3c68ad83547587e6f410a669e6356`. Other builders will not automatically have that key; keep it private and do not commit it.

## Build and test

Install JDK 17, Android SDK platform 34 and build tools 34.0.0. Set `ANDROID_HOME` to the SDK and accept its licenses.

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
./gradlew assembleRelease bundleRelease
```

The release build defaults to the local debug signing key when `KEYSTORE_PATH` is absent. To use a real key locally set `KEYSTORE_PATH` to an absolute path, plus `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Do not commit keystores or passwords. R8/resource shrinking is enabled for release. GitHub Actions runs tests, debug build and lint on pull requests and before tag releases.

## Signing and publishing

Create a stable production key and back it up securely; losing it prevents normal updates. For example:

```sh
keytool -genkeypair -v -keystore aura-release.jks -alias aura -keyalg RSA -keysize 4096 -validity 10000
base64 < aura-release.jks | tr -d '\n'
```

Add the output as the repository Actions secret `KEYSTORE_BASE64`; add `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`aura` above), and `KEY_PASSWORD`. Set all four or none: incomplete configuration fails the release. On a `vMAJOR.MINOR.PATCH` tag, the workflow derives `versionName` from the tag and `versionCode = major × 1,000,000 + minor × 1,000 + patch`, builds APK and AAB, and attaches both to a GitHub Release. If all secrets are absent, artifacts are named `debug-signed`; otherwise they are `release-signed`. Debug-signed AABs are for testing, not Play Store production use. Major must be at most 2000; minor and patch at most 999. Subsequent tags must have increasing version codes.

After creating an empty GitHub repository, replace the URL in these commands:

```sh
git init
git add .
git commit -m "Initial Aura Charge release"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPOSITORY.git
git push -u origin main
git tag v1.0.0
git push origin v1.0.0
```

## Permissions

`VIBRATE` is for the optional lock-in tick. `POST_NOTIFICATIONS` is needed for visible notifications on Android 13+. `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` support the opt-in monitor and its persistent notification; Play distribution requires the appropriate special-use review. `USE_FULL_SCREEN_INTENT` requests the optional full-screen notification, which Android may deny. Document pickers grant scoped access to chosen files; no broad storage permission or network permission is requested.

## License

Application code: MIT, see `LICENSE`. Fonts and audio have the separate terms above.
