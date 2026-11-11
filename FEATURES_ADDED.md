# Data Tracker feature inventory

Data Tracker focuses on the Android APIs that can safely report app-level data usage without sending personal usage data to a server. The app includes daily, weekly, monthly, mobile and Wi-Fi totals; top data consumers; foreground/background splits where Android provides them; app data-controls shortcuts; data-plan alerts; persistent local history; scheduled snapshots; and daily-cap notifications.

## Phone manager and file features

- App-health ranking combines cache, storage, foreground time and background-data indicators.
- Storage status and safe cleaning controls explain Android’s privacy boundaries instead of claiming to delete another app’s private files.
- Duplicate-photo discovery and gallery-trash cleanup helpers are included in the cleaner flow.
- Automatic cache-cleaner accessibility flow is opt-in and explains the permission clearly.
- Battery health card and setup checklist give actionable battery-optimization guidance.
- Data-plan cost tracking estimates the value of data already consumed.
- Shareable, local data reports help users keep a record without uploading their data.

## Added competitive essentials

1. **Privacy-first local processing** — no usage analytics are uploaded; a key trust advantage for a phone-manager app.
2. **14-day visual history and 30-day backfill** — users can identify recurring data spikes rather than only seeing one day.
3. **Boot recovery and background monitoring** — reminders and scheduled snapshots recover after a restart.
4. **Setup checklist** — gives friendly recovery actions when Usage Access, notifications, or battery settings are denied.
5. **App-health prioritization** — turns several weak signals into a practical “what should I fix first?” list.
6. **Plan price / naira burn estimate** — translates bytes into a meaningful local cost.
7. **Shareable report card** — lets users send a simple status report without exposing raw device data.

## Platform notes

Android does not allow a normal Play Store app to silently kill arbitrary apps, clear every app’s cache, or directly flip another app’s background-data restriction. Data Tracker opens the relevant Android settings page for the user to approve those actions. Full file browsing also requires the user to grant storage access; denied access must remain non-fatal.
