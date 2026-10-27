# SmartSpace – Smart Classroom & Lab Availability Finder (SAKEC)

A JavaFX desktop application (Java 21, FXML, JavaFX CSS, JDBC + SQLite, Maven) that lets students and teachers find free
classrooms and labs across seven floors, request bookings, and lets timetable coordinators manage rooms, the official
timetable, approvals, maintenance and analytics. Application logic is Java only.

## What you get
| Role | Screens |
|---|---|
| Student | Dashboard, Find rooms (floor selector + filters), My bookings, My reports, Notifications |
| Teacher | Dashboard (today's timetable, assigned rooms), Find rooms, Timetable (read only), My bookings, My reports, Notifications |
| Timetable Coordinator | Dashboard, Rooms (add/edit/deactivate), Timetable (add/edit/delete, conflicts), Booking requests, Maintenance, Analytics (+CSV), Users, Notifications |

## Requirements
* JDK 21 and Maven 3.9+ (IntelliJ IDEA or VS Code with the Java extension pack)
* Internet access the first time, so Maven can download JavaFX 21 and the SQLite driver

No database server is needed: SQLite creates `smartspace.db` in the folder you start the app from.

## Build and run
```bash
mvn clean package          # compiles and runs the tests
mvn javafx:run             # starts the app
```
**IntelliJ IDEA:** File > Open > select `pom.xml` > Open as Project. Wait for Maven to import, then run
`com.sakec.smartspace.app.Launcher` (or use the Maven tool window: Plugins > javafx > javafx:run).
**VS Code:** open the folder, let the Java extension import the Maven project, run `Launcher`
or `mvn javafx:run` in the terminal.

## First start: creating accounts (no hard-coded admin)
Nobody can sign up or pick a role. Accounts are created in one of two explicit ways:

1. **Real coordinator account** (recommended for real use):
   ```bash
   mvn javafx:run -Djavafx.args='--create-coordinator your.name@sakec.ac.in "Your Name"'
   ```
   A one-time temporary password is printed in the console. Sign in with it, then you are forced to choose your own.
   After that, add students and teachers from the **Users** screen.
2. **Sample data for development/demo only:**
   ```bash
   mvn javafx:run -Djavafx.args='--seed'
   ```
   Creates sample rooms (`SAMPLE-xxx`), a sample weekly timetable (flagged as sample data), and accounts
   `coordinator.sample@…`, `teacher1.sample@…`, `student1.sample@…` (and teacher2/3, student2).
   The temporary password is printed once; set `SMARTSPACE_DEV_PASSWORD` to choose it.
   Every account must change it at first sign-in. Seeding only runs when the database has no users.
   **Never present sample data as the real college timetable or room inventory.**

## Configuration
Edit `src/main/resources/config/app.properties`, or create `smartspace.properties` next to where you start the app, or use
environment variables (`SMARTSPACE_DB_URL`, `SMARTSPACE_AUTH_ALLOWEDDOMAINS`, …).
* `auth.allowedDomains` – accepted e-mail domains (comma separated). The default is `sakec.ac.in`;
  **confirm the real domain(s) with the college IT office** (students and staff may differ).
* `auth.maxFailedAttempts`, `auth.lockMinutes` – lock-out after repeated wrong passwords.
* `ops.startHour`, `ops.endHour` – operating hours used for utilization percentages (Mon–Sat).
* `ui.reducedMotion=true` – switches animations off.
* Optional font: put `Inter-Regular.ttf`, `Inter-Medium.ttf`, `Inter-SemiBold.ttf`, `Inter-Bold.ttf` in
  `src/main/resources/fonts/`. Without them the app falls back to Segoe UI / system fonts.

## How availability works
Statuses, in priority order: **Unavailable** (room deactivated) → **Under Maintenance** (maintenance record that blocks the room and
is not resolved) → **Occupied** (official timetable) → **Reserved** (approved booking) → **Available**.
Time ranges are half-open, so 10:00–11:00 and 11:00–12:00 do not clash. Pending requests do not block a room;
approval re-checks every conflict. Timetable entries can repeat weekly until a chosen date (max 60 weeks at a time).

## Floor map
The Floor Map supports cursor-centered wheel zoom, drag panning, per-floor view restoration, an overview navigator,
location search, and room-detail actions. Region geometry is normalized against the bundled floor-plan images.
Coordinators can draw a rectangular replacement for a selected region, save it as a draft, assign an existing room,
and validate/publish the geometry. Unpublished drafts are visible only to coordinators. Labels without a unique,
type-compatible room association remain unconfigured and do not receive an availability status.

Indoor route drawing is intentionally unavailable until verified corridor connections and floor-to-floor transitions
are configured; the current map images alone are not sufficient to infer a safe walkable graph.

## Architecture
```
com.sakec.smartspace
  app/         MainApp, Launcher, SceneManager, Bootstrap (seeding / coordinator creation), Events
  controller/  LoginController, MainController (FXML controllers)
  view/        Dashboard, Rooms, Timetable, Bookings, Maintenance, Notifications, Users, Analytics pages + Dialogs
  ui/          reusable components: StatCard, RoomCard, RoomSearchPanel, TimetableGrid, Modal, Toast, Tables, ...
  service/     business rules + permission checks (Auth, Availability, Booking, Timetable, Report, ...)
  dao/         JDBC data access (prepared statements only)
  model/       records and enums
  security/    PasswordHasher (PBKDF2-HMAC-SHA256), Session, Permissions
  util/        Db, Config, Validators, Async, Log, CsvExporter, Dt, errors
resources: fxml/ css/app.css config/app.properties database/schema.sql database/seed.sql
```
`database/schema.sql`, `database/seed.sql` (sample rooms), and `database/inventory.sql` (real fourth- and fifth-floor rooms)
are also copied at the project root for reference; the app runs the copies bundled in `src/main/resources/database`.
The inventory is inserted idempotently at startup; the listed rooms currently use a capacity of 60.

## Security notes
* Passwords: PBKDF2-HMAC-SHA256, 210,000 iterations, random salt; constant-time comparison; never logged.
* Role checks are in the **service layer** (`Permissions.require`), not only hidden buttons.
* Every query is a `PreparedStatement`. Users see safe messages; technical details go to the log only.
* Booking approval and creation run in one database transaction guarded by a lock. SQLite allows one writer at a time, which
  also serialises concurrent instances. If you later move to MySQL, add a row lock (`SELECT … FOR UPDATE`) or an exclusion rule.
* CSV exports neutralise cells starting with `= + - @` (spreadsheet formula injection).
* "Remember me" stores only the e-mail address (Java Preferences), never the password.

## Tests
`mvn test` runs `BackendFlowTest` (hashing, e-mail/password rules, overlap logic, unauthenticated access is rejected).

## Known limits (be honest with your examiners)
* Students are not linked to classes, so schedule-change notifications go to the affected **faculty**, not to students.
* There is no self-service "forgot password"; a coordinator resets it from the Users screen (temporary password, forced change).
* Timetable entries must start and end on the same day. Booking windows are 15 minutes to 6 hours, up to 60 days ahead.
* Room inventory beyond the listed fourth- and fifth-floor rooms must be added on the Rooms screen.
