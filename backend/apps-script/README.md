# Apps Script alert monitor

This folder contains the server-side five-minute HKO alert monitor. It uses the
official `warnsum`, `warningInfo`, and `swt` endpoints, stores a stable state
snapshot, and sends only issue/update/cancel changes through FCM HTTP v1.
Notifications are data-only so Android always builds the expandable notification
with the complete HKO message and routes taps to the matching alert tile.

## One-time owner setup

1. Create or open a Google Apps Script project, enable the manifest in project
   settings, and copy `Code.gs` and `appsscript.json` into it.
2. Add these **Script Properties** (do not paste values into source code):
   `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, and `FIREBASE_PRIVATE_KEY`.
   Use a newly generated service-account key with permission to send Firebase
   Cloud Messaging messages. A multiline private key or a value containing
   literal `\\n` line breaks is accepted.
3. Run `sendTestNotification` once and approve the requested permissions.
4. Run `installFiveMinuteTrigger` once. It removes duplicate monitor triggers
   and installs exactly one five-minute trigger.

The first `checkWeatherUpdates` run silently records the current HKO state, so
deploying the monitor does not resend every alert already in force.

After replacing an older `Code.gs` with this version, the `HKO_ALERT_STATE_V3`
key intentionally creates one new silent baseline. The monitor no longer treats
an unchanged warning's `updateTime` as a content update.

## Operations

- Run `resetAlertBaseline` if the saved state is corrupt. The next run becomes
  another silent baseline.
- Inspect **Executions** in Apps Script for HKO, OAuth, or FCM errors.
- Rotate the service-account key in Google Cloud and replace only the Script
  Property value; no repository change is needed.
