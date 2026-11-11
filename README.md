# Data Tracker - by CrestPoint

Native Android app (Kotlin). Per-app data usage, local history, alerts, charts,
storage health, cache-cleaner helpers and app-health controls. The Android
application id is `com.crestpoint.datatracker`.

## Build an APK

This environment uses Gradle directly (there is no Flutter SDK installed):

```bash
gradle assembleRelease --no-daemon
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`.

If you add Flutter later, the equivalent Flutter command requested for a Flutter
project would be `flutter build apk --release`; this is a native Kotlin project.

## Termux quick start

```bash
termux-setup-storage                      # once, accept the popup
cp ~/storage/downloads/DataTracker-v2-termux.zip ~
cd ~ && pkg install -y unzip && unzip DataTracker-v2-termux.zip
cd DataTracker
bash termux-setup.sh                      # installs git, gh, java; GitHub login
bash publish.sh "first build"             # creates private repo, pushes, builds, downloads APK
bash make-keystore.sh                     # optional but recommended: your own signing key
bash publish.sh "signed build"
```

Every later change: edit files, then `bash publish.sh "what changed"`.
The APK lands in `Downloads/datatracker-apk/` and Termux tries to open the installer
(allow "Install unknown apps" for Termux if asked).

No secrets? The workflow still builds, signed with a throwaway debug key.
Switching to your own key later means uninstalling once (signatures differ).

## What's in v2
- Permanent history: hourly background snapshot into SQLite, 30-day backfill, 14-day chart
- Alerts tab: daily cap per app and background-data threshold, notifications
- Charts: daily stacked bars (mobile/Wi-Fi), per-app hourly bars
- Per-app buttons: "Restrict data" and "App info" open Android's own settings

## Not included
- Which servers an app contacts (needs a VpnService build, like NetGuard)

## Data-plan threshold notifications

Open **Alerts** in the app to save a plan size, carrier, start date, and expiry
date. The app keeps plan-cycle snapshots locally, checks them every 30 minutes,
and warns at 80%, 90%, and 100%. Each threshold fires once per plan. Long-press
the alerts switch to reveal the hidden debug override, enter a percentage, tap
Save, and open Dashboard to test notifications without consuming data.

The notification action opens `https://crestpoint.name.ng`. To enable the
optional Supabase event sink later, create the table with:

```sql
create table if not exists public.app_events (
  id uuid primary key default gen_random_uuid(),
  event_type text not null,
  created_at timestamptz not null default now()
);
```

The current build does not include Supabase credentials or analytics network calls.
