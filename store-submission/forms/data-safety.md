# Data safety (Play Console › Policy › App content › Data safety)

Google's definition: data is "collected" when it leaves the device. Data handled only on the device isn't collected.

1. **Does your app collect or share any of the required user data types?** No.
   - Network: None (no INTERNET permission). Nothing can be sent anywhere.
   - No analytics, crash reporting, ads or other SDKs that send data.
2. The security questions (encryption in transit, deletion requests) don't apply when nothing is collected.

Resulting label: **No data collected. No data shared with third parties.**
