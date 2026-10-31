# Repository coding and ownership rules

- Follow PROJECT_CONTRACT.md for the exact model fields, database schema, and frozen public APIs.
- Do not rename or change public shared APIs. Coordinate contract changes with Member 1 first.
- Base package: com.example.studyschedule. Application code must be Java and layouts XML.
- Use SQLiteOpenHelper and SharedPreferences. No Jetpack Compose, Room, Firebase, or Kotlin application code. Kotlin DSL is permitted only for Gradle build scripts.
- Preserve the existing Android project structure. Build only the requested scope; do not implement the whole app as part of foundation work.
- Member 1 owns model/, data/, auth/, studentadmin/ and shared build/manifest configuration.
- Member 2 owns subject/ and schedule/. Prefix module XML filenames with subject_ or schedule_.
- Member 3 owns MainActivity.java, activity_main.xml, calendar/, notification/ and related XML UI. Other members must leave MainActivity.java and activity_main.xml untouched.
- Only Member 1 edits AndroidManifest.xml and Gradle files; other members report required edits.
- Dates: yyyy-MM-dd TEXT. Times: HH:mm TEXT. Statuses: Pending, Confirmed, Cancelled.
- Keep incomplete database operations clearly marked TODO with compile-safe failure/null/empty-list placeholders. Never present placeholders as functional authentication, CRUD, or conflict validation.
- Enable SQLite foreign keys. Preserve unique username/studentCode/subjectCode and the student-account relation.
- Run .\gradlew.bat assembleDebug after Java/XML changes and report the result.
- Do not commit, push, reset, or change Git branches unless explicitly authorized by the user.
