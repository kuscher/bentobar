# Data safety (Play Console › Policy › App content › Data safety)

Google's definition: data is "collected" when it leaves the device, also when it goes straight to a third
party. Data handled only on the device isn't collected.

## Up to 0.8
1. **Does your app collect or share any of the required user data types?** No (no INTERNET permission).

Resulting label: **No data collected. No data shared with third parties.**

## From 0.9 (filled in on 2026-10-05, sent for review with the 0.9 release)
0.9 has the INTERNET permission. Two items use it, Weather and Flight, and only after the user set the item up
on that device (Setup › Online services has a switch for each). Nothing goes to the developer: BentoBar has no
server, no analytics, no crash reporting and no ads. Requests go from the device to the two services directly.

| What leaves the device | To | When | Declared as |
|---|---|---|---|
| The text typed into the city search | Open-Meteo (geocoding) | On each press of Search | App activity › In-app search history |
| The coordinates of the chosen city, rounded to 0.01° | Open-Meteo (forecast) | About every 30 minutes while the bar is on screen; on opening the menu; Refresh | Location › Approximate location (it is the city the user chose, often their own) |
| With My location (since 1.3): the device's approximate location (`ACCESS_COARSE_LOCATION`), rounded to 0.1° | Open-Meteo (forecast) | The same; Android is asked for the location while such an item is on screen, waits behind ‹ to show for rain or snow, or BentoBar's settings window is open: every 30 minutes once it knows, and when it doesn't, after 1, 2, 5 and 15 minutes, then every 30 (asking Android sends nothing) | Location › Approximate location (the same type as above: no new answer, the label is unchanged) |
| The flight number | AirLabs | When a flight is tracked and while it is followed | App activity › In-app search history |
| The user's own AirLabs key | AirLabs | With each of those requests | Personal info › User IDs (the form has no type for a credential; this is the nearest: it identifies the user's account at that service) |
| The IP address | both | With every request, as for any website | Not a declared type by itself; said in the app and on the privacy page |

The answers given:
1. **Does your app collect or share any of the required user data types?** Yes.
2. **Is all of the user data collected by your app encrypted in transit?** Yes (HTTPS only; cleartext is off
   in the network security config).
3. **Account creation:** "My app does not allow users to create an account", and users cannot log in with an
   account made elsewhere. **Do you provide a way for users to request that their data is deleted?** The form
   marks this optional and it was left unanswered: the developer holds no data to delete; what the two services
   keep is theirs (the AirLabs key is the user's own account there), and Open-Meteo's logs go after 90 days.
4. **Approximate location:** collected, not shared; optional (only with the Weather item); purpose: App
   functionality. **Not** "processed ephemerally": the form's word for data that is only held in memory for
   one request, and such data is then left off the listing. The developer stores nothing, but Open-Meteo
   keeps request logs for 90 days (its terms, and the app's own About page says so), so the type is declared
   and shown.
5. **In-app search history:** collected, not shared; optional (Weather's city search, Flight's number);
   purpose: App functionality; not processed ephemerally.
   **User IDs:** collected, not shared; optional (only with a Flight item and a key of the user's own); purpose:
   App functionality; not processed ephemerally.
6. **"Shared"** (a transfer to a third party): **No**, under the form's exception for a transfer the
   user starts themselves after a prominent in-app disclosure. Both items show the words before the first
   request ("Weather comes from Open-Meteo…", "Flight times come from AirLabs…") and send nothing before the
   user presses Search or Use my location, or saves a key. Read more strictly, the same three types would be
   declared as shared for App functionality as well; nothing else would change.

Not collected, and why: media titles and artwork (read on the device only, with notification access, never
stored or sent); calendar events; device and battery readings; the layout.

Resulting label: **Data collected: Personal info (User IDs), Location (Approximate location), App activity
(In-app search history). No data shared with third parties. Data is encrypted in transit.**

Changed with 0.9 outside this form: the privacy page (googlebook.studio/privacy/bentobar), the store
description's "Private" paragraph (`listing/en-US/full-description.txt`), and the Accessibility declaration's
video: the consent screen did not change, but the description Android shows on the page with the switch did
(0.8's ended "It has no internet permission"), so the video was recorded again with the 0.9 build.

Not changed: "Sign in details" says no part of the app is restricted. The Flight item needs a key of the user's
own from AirLabs; if a review asks for access to it, the reviewers need a key to use.
