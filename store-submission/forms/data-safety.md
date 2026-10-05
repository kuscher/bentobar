# Data safety (Play Console › Policy › App content › Data safety)

Google's definition: data is "collected" when it leaves the device, also when it goes straight to a third
party. Data handled only on the device isn't collected.

## Up to 0.8
1. **Does your app collect or share any of the required user data types?** No (no INTERNET permission).

Resulting label: **No data collected. No data shared with third parties.**

## From 0.9: a draft, to be confirmed by the owner before the form is changed
0.9 has the INTERNET permission. Two items use it, Weather and Flight, and only after the user set the item up
on that device (Setup › Online services has a switch for each). Nothing goes to the developer: BentoBar has no
server, no analytics, no crash reporting and no ads. Requests go from the device to the two services directly.

| What leaves the device | To | When | Proposed data type |
|---|---|---|---|
| The text typed into the city search | Open-Meteo (geocoding) | On each press of Search | App activity › In-app search history |
| The coordinates of the chosen city, rounded to 0.01° | Open-Meteo (forecast) | About every 30 minutes while the bar is on screen; on opening the menu; Refresh | Location › Approximate location (it is the city the user chose, often their own) |
| The flight number, and the user's own AirLabs key | AirLabs | When a flight is tracked and while it is followed | App activity › In-app search history (the number); the key is the user's credential for that service, not a data type of the form |
| The IP address | both | With every request, as for any website | Not a declared type by itself; said in the app and on the privacy page |

Proposed answers:
1. **Does your app collect or share any of the required user data types?** Yes.
2. **Is all of the user data collected by your app encrypted in transit?** Yes (HTTPS only; cleartext is off
   in the network security config).
3. **Do you provide a way for users to request that their data is deleted?** The app keeps nothing off the
   device and has no accounts. Answer as Play's form allows for an app without accounts; deletion at the two
   services is theirs (the AirLabs key is the user's own account there).
4. **Approximate location:** collected, not shared; optional (only with the Weather item); purpose: App
   functionality; processed ephemerally by the service, not stored by the developer.
5. **In-app search history:** collected, not shared; optional (Weather's city search, Flight's number);
   purpose: App functionality.
6. **"Shared"** (a transfer to a third party): proposed **No**, under the form's exception for a transfer the
   user starts themselves after a prominent in-app disclosure. Both items show the words before the first
   request ("Weather comes from Open-Meteo…", "Flight times come from AirLabs…") and send nothing before the
   user presses Search or saves a key. If the owner reads the exception more strictly, the same two types are
   declared as shared for App functionality instead; nothing else changes.

Not collected, and why: media titles and artwork (read on the device only, with notification access, never
stored or sent); calendar events; device and battery readings; the layout.

Also to change with 0.9, outside this form: the privacy page (googlebook.studio/privacy/bentobar), the store
description's "Private" paragraph (done in `listing/en-US/full-description.txt`), the permission list in
`app-content.md`. The Accessibility declaration and its video stay valid: the consent screen did not change.
