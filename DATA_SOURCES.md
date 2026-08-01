# Data sources and precedence

| Information | Primary source | Secondary/fallback | Presentation rule |
| --- | --- | --- | --- |
| Current temperature, humidity, rainfall, UV, icon | HKO `rhrread` | none for an available HKO field | Nearest matching official station/district |
| Active warnings | HKO `warnsum` | none | Authoritative active-state source |
| Warning detail | HKO `warningInfo` | summary title | Joined official detail paragraphs |
| Special weather tips | HKO `swt` | none | Separate magenta alert tiles |
| Nine-day forecast | HKO `fnd` | none | Official icon, temperature, humidity, PSR and text |
| Weather overview | HKO `flw` | none | General situation, forecast and outlook |
| Sun and moon times | HKO `SRS` / `MRS` | none | Hong Kong civil times |
| Tides | HKO `HLT` | none | Nearest supported tide station |
| Visibility | HKO `LTMV` | none | Latest available reading |
| Feels-like, dew point, pressure, local wind/gust | Open-Meteo | none | Labelled as a secondary estimate |
| Hourly local estimate | Open-Meteo | none | Kept separate from official HKO forecast |

All APIs are requested over HTTPS. A missing optional HKO astronomy dataset does
not block the main current-weather snapshot. HKO fields always take precedence
when both providers expose the same observation.
