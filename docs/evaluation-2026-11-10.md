# Nexus evaluation (commit 7fd6d17), Nov 10 2026
Full teardown was shared with Pankaj as a private page; key findings below.
Not built (no JDK/SDK on box, no gradlew, no tests). Static read only.
Top bugs:
1. Actions.kt 14-28: Uber link lacks dropoff lat/lng (required per Uber docs); Ola uses web-only drop_name, missing drop_lat/drop_lng + mandatory utm_source; Rapido scheme undocumented. ArcGIS suggest gives text only (Actions.kt 95).
2. Model.kt 79-88 international(): 10-digit IN numbers starting 91 -> "+91xxxxxxxx" (missing CC); leading 0 -> +910...
3. HomeScreen.kt 71/423: Edit-screen contact saved raw (no CC) -> wa.me fails.
4. Plan.kt 55-66: "pick me up at home" => dropoff=home.
5. Plan.kt CALL regex: Sample "Call me at" -> "Find me in contacts"; Hinglish "Aai ko call karo" -> "karo".
Play: new personal accounts need 12 testers x 14 days closed test -> run tester round as Play closed test.
