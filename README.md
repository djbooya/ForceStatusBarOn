# Force Status Bar On

A lightweight Android background service that keeps a status bar on screen, even while full-screen apps hide the system one. Built for car head units, where navigation and media apps often go full-screen and take the clock with them.

## Features

- **One simple choice** — open the app and pick **Force Status Bar On: Yes / No**. The choice is saved, applied and confirmed with a toast, and the app exits on its own
- **Runs in the background** — a foreground service keeps the bar up; no screen stays open
- **Only appears when needed** — while the system status bar is showing, nothing extra is drawn; as soon as an app hides it, a replica bar (clock, plus battery level if the device has a battery) takes its place
- **Never blocks touches** — the bar passes every tap straight through to the app underneath
- **Automate support** — a headless entry point starts the service with no user interface, so an Automate flow can turn it on, off, or restart it silently
- **Boot auto-start** — if set to Yes, the service comes back after a reboot or app update

## Installation

1. Download the latest APK (`ForceStatusBarOn-*.apk`) - https://github.com/djbooya/ForceStatusBarOn/tree/main/app/build/outputs/apk/debug
2. Copy to a USB drive or radio storage
3. On the radio, enable "install unknown apps" for your file manager and open the APK
4. Open **ForceStatusBarOn** and tap **Yes**
5. The first time, Android opens the **Display over other apps** page — turn it on for ForceStatusBarOn. The bar starts within a couple of seconds of the grant; there is no need to reopen the app
6. If your unit force-stops apps on sleep, set up Automate — see [Running under Automate](#running-under-automate)

Back, or tapping outside the prompt, exits without changing anything.

### If the "Display over other apps" page is missing

Some head units ship without that settings page. Grant the permission over ADB instead:

```bash
adb shell appops set com.djbooya.forcestatusbaron.debug SYSTEM_ALERT_WINDOW allow
```

(For a release build, drop the `.debug` suffix.)

## How It Works

Android does not let a sideloaded app force the *system* status bar to show over a full-screen app — that decision belongs to the app in front. So ForceStatusBarOn does the next best thing:

1. A tiny invisible overlay watches whether the system status bar is currently showing
2. When an app hides it, a replica bar is drawn as an overlay in exactly the same place and at the same height
3. When the system status bar comes back, the replica is removed so the two never stack

The replica shows the time (following the system 12/24-hour setting) and, on devices with a battery, the battery percentage. It does not show notification icons — those belong to the real status bar, which an app cannot read without notification access.

## Permissions Used

- `SYSTEM_ALERT_WINDOW` — draw the replica bar over other apps
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` — keep the service running in the background
- `RECEIVE_BOOT_COMPLETED` — restore the saved setting after a reboot

## Running under Automate

[LlamaLab Automate](https://llamalab.com/automate) can start ForceStatusBarOn without ever showing its prompt. Any of the following works; pick whichever fits your flow.

### Option 1 — Activity start (no UI, can also set Yes / No)

Use an **App start** block (Automate calls it *App start* / *Activity start*) with:

| Field | Value |
|---|---|
| Package | `com.djbooya.forcestatusbaron.debug` |
| Activity class | `com.djbooya.forcestatusbaron.AutomateActivity` |
| Action | one of the actions below |

| Action | Effect |
|---|---|
| `com.djbooya.forcestatusbaron.action.START` | Apply the saved setting (starts the bar if it is Yes) — silent |
| `com.djbooya.forcestatusbaron.action.ENABLE` | Save **Yes** and start the bar, with a toast |
| `com.djbooya.forcestatusbaron.action.DISABLE` | Save **No** and stop the bar, with a toast |

`AutomateActivity` has no layout and finishes immediately, so nothing appears on screen and whatever was in front stays in front.

### Option 2 — Service start (watchdog)

| Field | Value |
|---|---|
| Package | `com.djbooya.forcestatusbaron.debug` |
| Class | `com.djbooya.forcestatusbaron.StatusBarService` |
| Foreground | **Yes** |

This only applies the saved setting: if it is No, the service stops again straight away.

> ⚠️ **The package and class names do not match.** The debug build appends `.debug` to the
> *application ID* only — the Java class name never changes. Using
> `com.djbooya.forcestatusbaron.debug.StatusBarService` will fail with a class-not-found error.
> For a release build the package is `com.djbooya.forcestatusbaron` and the class is unchanged.

> ⚠️ **Foreground must be Yes.** Starting the service with Foreground = No calls
> `startService()`, which Android 8+ refuses for a background app.

### Watchdog for units that force-stop apps on sleep

Some head units **force-stop** packages on sleep, which deletes alarms and blocks boot receivers until something launches the app again. If the bar stops coming back after sleep, run this as an Automate flow and enable **Run on system startup** in Automate's settings:

```
   ┌─────────────────────────────┐
   │ Flow beginning              │
   └──────────────┬──────────────┘
                  ↓
   ┌─────────────────────────────┐
   │ Service start               │   ← restarts ForceStatusBarOn
   │   Package:  com.djbooya.forcestatusbaron.debug
   │   Class:    com.djbooya.forcestatusbaron.StatusBarService
   │   Foreground: Yes
   └──────────────┬──────────────┘
                  ↓
   ┌─────────────────────────────┐
   │ Delay                       │
   │   5 minutes                 │
   └──────────────┬──────────────┘
                  ↓
          (loop back to Service start)
```

Starting an already-running service is harmless, so a shorter delay only costs a little CPU.

## Debug Logs

The app writes a log so problems can be diagnosed on units without ADB.

**Log Location:** `Android/data/com.djbooya.forcestatusbaron.debug/files/logs/forcestatusbaron.log` (accessible via File Manager). The path is also shown in the app's prompt.

**Log Contents:**
- Android version, device model and display size each time the service starts
- Whether **Display over other apps** is allowed
- The replica bar's height, and whether its window was added (or the error if it wasn't)
- Every change in the system status bar's reported state, e.g. `Real status bar HIDDEN (isVisible=false top=0 ...) -> replica shown`
- Starts from Automate, boot and app updates

**Automatic Cleanup:** Logs older than 24 hours are deleted when the app next starts.

## Release Notes

### v1.1 (Oct 20, 2026)
- **Fixed:** On some head units the home screen hides the status bar while it is still reported as "visible" with zero height, so the replica never appeared. The status bar now only counts as showing if it is reported visible **and** actually takes up space
- **Fixed:** The invisible window that watches the status bar now has zero opacity, so it can never be counted as covering the app and block taps on Android 12+
- **Added:** Debug log at `Android/data/com.djbooya.forcestatusbaron.debug/files/logs/forcestatusbaron.log`, recording every status bar change, the overlay permission and any error adding the bar. See [Debug Logs](#debug-logs)
- **Added:** The prompt now shows the log location, and warns when **Display over other apps** isn't allowed (the bar can't be drawn without it)
- **Note:** The transparent bar with a home button and clock that appears in split screen and in Waze is the radio's own status bar. Pulling it down opens the radio's running-apps panel. ForceStatusBarOn stays hidden while it is showing
- **Download:** [ForceStatusBarOn-1.1-debug.apk](https://github.com/djbooya/ForceStatusBarOn/raw/main/app/build/outputs/apk/debug/ForceStatusBarOn-1.1-debug.apk)

### v1.0 (Oct 20, 2026)
- **Added:** Single-screen prompt — **Force Status Bar On: Yes / No**. Choosing saves the setting, starts or stops the service, confirms with a toast (`Force Status Bar On: Yes` / `No`) and exits
- **Added:** Background foreground service that draws a replica status bar (clock, plus battery when present) whenever a full-screen app hides the system status bar, and removes it when the system bar returns. The bar is non-touchable, so taps pass through to the app underneath
- **Added:** Headless `AutomateActivity` for Automate with `START`, `ENABLE` and `DISABLE` actions — no user interface is shown. `StatusBarService` is exported for Automate's **Service start** block. See [Running under Automate](#running-under-automate)
- **Added:** Restores the saved setting after reboot and after an app update
- **Note:** If **Display over other apps** has not been granted yet, choosing Yes opens that settings page; the service waits for the grant and starts drawing the bar as soon as it is given
- **Download:** [ForceStatusBarOn-1.0-debug.apk](https://github.com/djbooya/ForceStatusBarOn/raw/main/app/build/outputs/apk/debug/ForceStatusBarOn-1.0-debug.apk)

## Troubleshooting

**Toast says Yes, but no bar appears over a full-screen app**
- Open the app: if the prompt shows a "Display over other apps" warning, allow it for ForceStatusBarOn (or use the ADB command above)
- Check that the persistent "Keeping the status bar visible" notification is present — if not, the service isn't running
- Open the [debug log](#debug-logs) and look at the last `Real status bar ...` line written while the bar was missing. If it says `SHOWING` while you can't see the radio's bar, the radio is hiding its bar in a way Android doesn't report

**Bar disappears after the radio sleeps**
- Your unit is probably force-stopping the app. Set up the [Automate watchdog](#watchdog-for-units-that-force-stop-apps-on-sleep)

**Two status bars stacked on top of each other**
- The replica should hide whenever the system bar is showing. If your unit's launcher draws its own fake status bar rather than using Android's, ForceStatusBarOn can't tell it apart — set the prompt to **No**

## Building from Source

```bash
cd ForceStatusBarOn
export JAVA_HOME="/path/to/Android/Studio/jbr"
./gradlew assembleDebug
```

Output APK: `app/build/outputs/apk/debug/ForceStatusBarOn-1.1-debug.apk`

For a release build:
```bash
./gradlew assembleRelease
```

(Requires a keystore — see Android documentation on signing.)

## License

No license — provided as-is for personal use on your radio.
