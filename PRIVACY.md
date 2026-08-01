# Privacy

Weather Metro does not require an account and contains no advertising or
analytics SDK.

When precise location is enabled, Android provides the device coordinates to
the app so it can resolve a local street/district and choose nearby observation
and tide stations. Coordinates are sent to Open-Meteo to obtain local hourly
estimates. They are not sent to the project owner, Firebase, Apps Script, or the
Hong Kong Observatory. Disabling precise location uses a fixed central Hong
Kong fallback.

The app stores UI preferences and one weather response cache in private local
app storage. Android backup is disabled. Clearing app storage or using the
in-app clear-cache command removes the weather cache.

If notifications are enabled, Firebase Cloud Messaging registers the app
instance and subscribes it to the common `hko_alerts` topic. The server sends the
same HKO warning update to the topic and receives no device location. Firebase
processing is subject to Google's applicable privacy terms.

Official HKO tools opened from the `tools` Pivot run in the user's external web
browser and are subject to HKO website policies.
