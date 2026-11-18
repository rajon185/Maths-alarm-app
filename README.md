# Maths Alarm

An Android alarm clock that makes you **solve problems to turn it off**.

## Features

- **Multiple alarms**, each with its own time, label and **repeat days** (Mon–Sun, or one-off).
- **Problem types** (pick one or several per alarm; each problem is drawn at random from your selection):
  - Arithmetic: `+ − × ÷`
  - Equations: solve for *x*
  - Sequences: find the next number
  - Memory: memorise a number, then type it back
  - Typing: retype a random string exactly
- **Difficulty**: Easy / Medium / Hard.
- **Number of problems** per alarm: 1–20.
- **Snooze** per alarm: on/off, length (1–30 min) and maximum snoozes (1–10 or unlimited).
  Turn snooze off (or let the snoozes run out) and the only way to stop the alarm is to solve the problems.
- **Silence while solving**: tapping *Dismiss* mutes the alarm for a configurable 10–120 s.
  Each correct answer restarts the quiet period. If time runs out the alarm rings again until you finish
  (you can tap to silence it again).
- Wrong answers give you a new problem; progress already made is kept.
- Per-alarm ringtone and vibration, shows over the lock screen, survives reboots, and can't be escaped with the back button.
- *Save & test* lets you try an alarm right away.

## Getting the APK

Every push runs the **Build APK** GitHub Actions workflow (`.github/workflows/build.yml`), which runs the
unit tests and uploads `maths-alarm-apk` (contains `app-release.apk` and `app-debug.apk`) as a build artifact.
Download it from the run page, copy the APK to your phone and install it (allow "install unknown apps").

Or build locally with Android Studio / the Android SDK:

```
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

## First run

The home screen shows a red card for anything the app still needs. Tap **Fix** on each:
notifications, *Alarms & reminders* (Android 12), *full-screen alarms* (Android 14+) and,
recommended, turning off battery optimisation so the system never delays an alarm.

## Project layout

```
app/src/main/java/com/mathsalarm/app/
  model/Alarm.kt              alarm settings + JSON
  data/AlarmStore.kt          persistence (SharedPreferences)
  alarm/AlarmScheduler.kt     AlarmManager.setAlarmClock scheduling, snoozes
  alarm/NextTrigger.kt        next ring-time calculation
  alarm/AlarmReceiver.kt      fires the alarm
  alarm/BootReceiver.kt       reschedules after reboot / time change
  alarm/AlarmService.kt       foreground service: sound, vibration, temporary silence
  problems/ProblemGenerator   problem generation and answer checking
  ui/MainActivity, AlarmListScreen, AlarmEditScreen   alarm management (Jetpack Compose)
  ui/RingingActivity          lock-screen alarm + problem solving
```
