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
* Internet access the first time, so Maven can download JavaFX 21, the PostgreSQL driver, and the SQLite driver

Database options:
* **Supabase (PostgreSQL)** (Cloud / Production): Set `db.url=jdbc:postgresql://...` and credentials in `smartspace.properties` or environment variables `SMARTSPACE_DB_URL`, `SMARTSPACE_DB_USER`, `SMARTSPACE_DB_PASSWORD`.
* **SQLite (Local / Fallback)**: If no remote database is configured, SQLite creates `smartspace.db` locally.

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
Accounts can be created in three ways:

1. **Student self-registration** (new for this release):
   Students can register themselves directly from the login screen by clicking **"New student? Create an account"**.
   Only `@sakec.onmicrosoft.com` emails are accepted. The account is active immediately and the student can sign in
   right away. No coordinator action is required for student registration.
   The branch list and accepted domain are configurable — see **Configuration** below.

2. **Real coordinator account** (recommended for real use):
   ```bash
   mvn javafx:run -Djavafx.args='--create-coordinator your.name@sakec.ac.in "Your Name"'
   ```
   A one-time temporary password is printed in the console. Sign in with it, then you are forced to choose your own.
   After that, add teachers from the **Users** screen (self-registration is for students only).

3. **Sample data for development/demo only:**
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
* `auth.allowedDomains` – comma-separated list of domains accepted for any login. Defaults to
  `sakec.onmicrosoft.com,sakec.ac.in` (student Microsoft 365 + staff/coordinator domain).
  **Confirm the real domains with the college IT office.**
* `auth.studentDomains` – comma-separated list of domains accepted for **student self-registration only**.
  Default: `sakec.onmicrosoft.com`. Change this if the student Microsoft 365 tenant domain changes.
* `auth.branches` – pipe-separated (`|`) list of branch names shown in the sign-up dropdown.
  Defaults to eight SAKEC branches. Edit here to add or remove branches without recompiling.
* `auth.maxFailedAttempts`, `auth.lockMinutes` – lock-out after repeated wrong passwords.
* `ops.startHour`, `ops.endHour` – operating hours used for utilization percentages (Mon–Sat).
* `map.refreshSeconds` – floor-map data refresh interval, from 10 to 3600 seconds (default: 30).
* `mail.enabled=false` and `mail.smtp.*` – optional SMTP settings; when enabled, a welcome email is sent to new
  self-registered students and an account-creation or password-reset email to coordinator-created accounts.
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
location search, keyboard-accessible region selection, and room-detail actions. Search filters include floor, room type,
minimum capacity, equipment, and timetable availability. Select a date, start time, and duration to color mapped rooms
from one database snapshot: green means available, red means occupied by the timetable, orange means reserved by an
approved booking, and grey means maintenance, unavailable, or unknown. Pending requests do not block availability.
Conflicting records are treated as occupied/reserved and explicitly identified. This is schedule-based availability,
not sensor-based physical occupancy. The map refreshes at the configured interval; a failed refresh clears availability
colors, disables room actions, and shows a stale-data warning with the last successful refresh time. Polling stops when
the map page is hidden. Separate local SQLite database files do not synchronize across devices or application instances.
Region geometry is normalized against the bundled floor-plan images. Coordinators can draw a rectangular replacement
for a selected region, save it as a draft, assign an existing room, and validate/publish the geometry. Unpublished drafts
are visible only to coordinators. Labels without a unique, type-compatible room association remain unconfigured and
grey; they are never assumed to be available.

Indoor routes use a separate, persistent navigation graph; room rectangles and the floor-plan images are not treated
as evidence of walkable corridors. Coordinators can open **Navigation management** to place nodes on the actual floor
plans, configure walkable connections and room links, and review validation diagnostics. A draft can be published only
after nodes, room links, connections, and any required Floor 4-to-5 lift or staircase transition are verified and the
graph passes validation. Find Route uses Dijkstra's algorithm over published, verified connections and offers
step-by-step directions. Until a coordinator configures and publishes the real building connections, routing remains
disabled and reports the missing navigation data; no routes are pre-populated or inferred.

Coordinators can open **Estimate future** on the map for a rule-based estimate using the previous eight comparable
weekdays. It first excludes all rooms with current timetable, approved-booking, or maintenance conflicts, then ranks
eligible rooms by historical recorded use. It is advisory only; creating or approving a booking revalidates the
authoritative schedule. Analytics separates timetable minutes from approved-booking minutes by hour and day, reports
room-time utilization by floor, and lists booking counts/cancellations. It does not measure attendance, physical
occupancy, or no-shows because no sensor or verified check-in data is stored.

## Architecture
```
com.sakec.smartspace
  app/         MainApp, Launcher, SceneManager, Bootstrap (seeding / coordinator creation), Events
  controller/  LoginController, MainController, SignupController (FXML controllers)
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
* Self-registration: only `@sakec.onmicrosoft.com` emails accepted; email is normalised before validation; role is
  always `STUDENT` and is set exclusively by the service layer — callers cannot choose a different role.
  The form is locked while a request is in flight to prevent duplicate submissions.
* Role checks are in the **service layer** (`Permissions.require`), not only hidden buttons.
* Every query is a `PreparedStatement`. Users see safe messages; technical details go to the log only.
* Booking approval and creation run in one database transaction guarded by a lock. SQLite allows one writer at a time, which
  also serialises concurrent instances. If you later move to MySQL, add a row lock (`SELECT … FOR UPDATE`) or an exclusion rule.
* CSV exports neutralise cells starting with `= + - @` (spreadsheet formula injection).
* "Remember me" stores only the e-mail address (Java Preferences), never the password.

## Tests
`mvn test` runs 26 tests across 6 suites covering hashing, e-mail/password rules (including the student domain check),
overlap logic, unauthenticated access rejection, availability service, estimation service, report service, room label
matching, and navigation graph persistence.

## Known limits (be honest with your examiners)
* Students are not linked to classes, so schedule-change notifications go to the affected **faculty**, not to students.
* There is no self-service "forgot password"; a coordinator resets it from the Users screen (temporary password, forced change).
* Self-registration is available to students only (Microsoft 365 domain). Teachers and coordinators are added by a coordinator.
* Timetable entries must start and end on the same day. Booking windows are 15 minutes to 6 hours, up to 60 days ahead.
* Room inventory beyond the listed fourth- and fifth-floor rooms must be added on the Rooms screen.
