# WishesSender v1.2 (MVP)

**A smart special-day reminder, wish and gift suggestion system.**
CSE 224 – Database Management System Lab project.

| Layer    | Technology                                         |
|----------|----------------------------------------------------|
| Frontend | HTML, CSS, JavaScript (`fetch` API calls)          |
| Backend  | Java + Spring Boot 2.7 (REST API, JdbcTemplate)    |
| Database | MySQL 8 (tables, foreign keys, indexes, views)     |
| AI       | Google Gemini API (with template fallback)         |

---

## 1. What works in v1

| Module | What you can do |
|---|---|
| Registration & Login | Register, log in, log out. Passwords are BCrypt-hashed. |
| Connections | Add / edit / delete / search people, filter by relation (Family, Friend, ...). **Connections** page: all your connections in a table, filter by relation, religion, gender. |
| Events | Birthday is created automatically from date of birth. Add anniversaries, festivals, custom events. |
| Festival list | Upcoming festivals, suggested to each person by religion, one click to add. |
| Reminder scheduler | Background job runs every minute and creates in-app reminders (on the day + N days before). |
| Reminders page | Notification inbox, unread badge, "Run scheduler now" button for the demo, event filters. "Mark all read" clears the list; sending a wish for an event clears its reminder ("Show cleared" to see them again). |
| Wish Studio | Pick person, occasion, tone and language → Gemini writes the wish → edit, copy, save, mark as sent. |
| Template fallback | If AI is off, no API key, or the call fails, a wish template from the DB is used. |
| Gift suggestions | Gifts matched by budget, event type and interests, with a "Why this gift?" reason. |
| Wish history | Every saved/sent wish is stored and shown on the person's page. |
| Dashboard | Today's celebrations, next 30 days, stats, recent wishes. |
| Insights | Reports from SQL views + CSV export + Print/PDF. |
| Settings | Name, AI on/off, share interests with AI on/off, how many days before to remind. |
| Admin panel | Platform stats, users list, manage gift catalog and festivals. Admin sidebar: **People** (all registered users) and **Connections** (all connections, filter by person, religion, gender). |

**Not in v1 (planned for v2):** subscription plans, calendar month view, AI "why this gift" text,
custom reminder times, Spring Security.

---

## 2. Requirements

1. **Java JDK 8 or newer** (`java -version`)
2. **MySQL 8.0.16 or newer** (Docker `mysql:8.0`, or MySQL Server + Workbench) – CHECK constraints need 8.0.16+
3. **IntelliJ IDEA** (recommended – it has Maven built in) or Maven installed
4. *(Optional)* a **Gemini API key** – free from <https://aistudio.google.com/apikey>

---

## 3. Setup

### Step 1 – Start MySQL
Make sure MySQL is running on `localhost:3306`. You do **not** need to create the database
or tables – the app creates the `wishes_sender` database, all tables, views and seed data on start.

### Step 2 – Set your MySQL username and password
Open `src/main/resources/application.properties`. The defaults are user `root`, password `root`:

```properties
spring.datasource.username=${DB_USER:root}
spring.datasource.password=${DB_PASSWORD:root}
```

Change `root` after the colon to your own password (e.g. `${DB_PASSWORD:mypassword}`),
or set the environment variables `DB_USER` / `DB_PASSWORD`.

### Step 3 – (Optional) Add your Gemini API key
Never write the key in the code. Set it as an environment variable.

**PowerShell (same window you run the app from):**
```powershell
$env:GEMINI_API_KEY="paste-your-key-here"
```

**IntelliJ:** Run → Edit Configurations → *WishesSenderApplication* → Environment variables →
`GEMINI_API_KEY=paste-your-key-here`

Without a key the app still works – Wish Studio uses the templates from the database.

---

## 4. Run

### Option A – IntelliJ IDEA (easiest)
1. File → Open → select this folder (the one with `pom.xml`). Wait for Maven to download dependencies.
2. Open `src/main/java/com/wishessender/WishesSenderApplication.java`.
3. Click the green ▶ Run button.
4. Open **<http://localhost:8085>** in your browser.

### Option B – Command line
```powershell
.\mvnw.cmd spring-boot:run
```
(`mvnw.cmd` downloads Maven automatically the first time. If you have Maven installed, `mvn spring-boot:run` also works.)

Then open **<http://localhost:8085>**.

### Login
- **Admin:** `admin@wishes.com` / `admin123` (created automatically on first start)
- **User:** click *Register* on the login page and create your own account.

---

## 5. Demo flow (for the presentation)

1. Register a new user → you land on **Home**.
2. **People → + Add connection** – add someone whose date of birth is **today or tomorrow**
   (any year), add interests like `books, music`.
3. Their profile opens: birthday event already created, gift ideas, suggested festivals (click one to add it).
4. **Reminders → Run scheduler now** – a reminder appears and the 🔔 badge updates.
5. **Home** shows the person under *Today's Celebration* → **Send Wish** (or *Send wish* on the reminder).
6. **Wish Studio** – choose a tone → *Generate* → edit → *Mark as sent* → "Reminder cleared", the 🔔 badge drops.
7. **Connections** – all your connections in one table; filter by religion / gender.
8. **Insights** – charts from SQL views, export CSV.
9. **Settings** – turn AI off and generate again → template wish is used.
10. Log out, log in as **admin** → **Admin** page: stats, users, add a gift / festival.
11. Admin sidebar → **People** (all users) → *Connections* on a user → filter by religion / gender.

---

## 6. Project structure

```
wishes-sender-v1
├── pom.xml                                  Maven dependencies
└── src/main
    ├── java/com/wishessender
    │   ├── WishesSenderApplication.java     main() – starts the app + scheduler
    │   ├── config/DataSeeder.java           creates the default admin
    │   ├── config/SchemaMigrator.java       upgrades a v1.0 database (runs once, safe to re-run)
    │   ├── controller/                      REST API (one file per module)
    │   │   ├── AuthController.java          /api/auth/*
    │   │   ├── ConnectionController.java    /api/connections
    │   │   ├── EventController.java         /api/.../events, /api/festivals
    │   │   ├── WishController.java          /api/wishes (Wish Studio + history)
    │   │   ├── GiftController.java          /api/gifts/suggest
    │   │   ├── NotificationController.java  /api/notifications
    │   │   ├── DashboardController.java     /api/dashboard, /api/reports
    │   │   ├── SettingsController.java      /api/settings
    │   │   └── AdminController.java         /api/admin/*
    │   ├── service/
    │   │   ├── GeminiService.java           calls the Gemini REST API
    │   │   └── ReminderScheduler.java       @Scheduled job → notifications
    │   ├── controller/LookupController.java /api/lookups (allowed values)
    │   └── util/Auth, Db, Rules, ApiErrorHandler   helpers, validation, error messages
    └── resources
        ├── application.properties           DB + Gemini settings
        ├── schema.sql                       9 tables + 4 views + constraints
        ├── data.sql                         festivals, templates, gift catalog
        └── static/                          the website (HTML/CSS/JS)
            ├── index.html  home.html  people.html  person.html  connections.html  wish.html
            ├── reminders.html  festivals.html  insights.html  settings.html
            ├── admin.html  admin-people.html            (admin only)
            ├── css/style.css
            └── js/app.js                    api() helper + shared functions
```

---

## 7. Database design

**Tables:** `users`, `connections`, `event_types`, `events`, `festivals`, `notifications`, `wish_templates`, `wishes`, `gifts`

**Relationships:**
- `users` 1 — N `connections` 1 — N `events`
- `connections` 1 — N `wishes`, `wishes` N — 1 `events` (optional, must be the same person's event)
- `events` 1 — N `notifications`
- `event_types` 1 — N `events`, `gifts`, `wish_templates` (lookup table, `gifts.event_type` NULL = any event)
- The owner of a wish / notification is found through `connections.user_id` – no copied `user_id` columns.
- The date of birth is the connection's BIRTHDAY event (the API still returns it as `dob`).
- Foreign keys from child rows use `ON DELETE CASCADE` (deleting a user or person removes their events, wishes, reminders).

**Views:**
- `v_event_next` – every event with its next yearly date and `days_left` (used by dashboard, reminders, scheduler)
- `v_connections_by_relation`, `v_events_per_month`, `v_wishes_by_event_type` – reports

**Constraints / indexes:**
- Unique: user email; `(user_id, name)` on connections; one birthday per connection (`events.birthday_of`);
  `(event_id, for_date, days_before)` on notifications (a reminder is never created twice).
- CHECK: allowed values for relation, gender, religion, role, tone, language, source, status, gift category,
  festival religion; `gift_min >= 0`, `gift_max >= gift_min`, `price > 0`, `yearly` / flags 0 or 1,
  `days_before` and `remind_days_before` 0–30.
- Indexes on `connections.user_id`, `connections.name`, `events.event_date`.

The allowed values live in `util/Rules.java` (backend validation and `GET /api/lookups`) and in the CHECK
constraints of `schema.sql` (the database's own guard). Change both together.

The scheduler is a single SQL statement:
`INSERT IGNORE INTO notifications ... SELECT ... FROM v_event_next WHERE days_left = 0 OR days_left = remind_days_before`.

---

## 8. Troubleshooting

| Problem | Fix |
|---|---|
| `Access denied for user 'root'` | Wrong MySQL password – fix it in `application.properties` (Step 2). |
| `Communications link failure` | MySQL is not running, or not on port 3306. |
| `Port 8085 already in use` | Stop the other app, or change `server.port` in `application.properties`. |
| Wish always says "used a template" | `GEMINI_API_KEY` is not set in the window/run config that starts the app, or AI is turned off in Settings. |
| Gemini model error | Set another model, e.g. `$env:GEMINI_MODEL="gemini-2.0-flash"`. |
| Want a clean database | In MySQL Workbench: `DROP DATABASE wishes_sender;` then restart the app. |
| `409` "You already have a connection named ..." | Names are unique per user – add something like "Rahul (Office)". |
| `409` "... already has a birthday" | A person has one birthday – change the date of birth with **Edit** on their profile. |
| `Check constraint '...' is violated` at start | Run on MySQL **8.0.16+** (older versions do not support CHECK). |

---

## 9. Upgrading from v1.0.0 / v1.1.0

v1.1.0 changed the database design and v1.2.0 adds `notifications.is_cleared` (see `CHANGELOG.md`). Your data is upgraded **automatically** on the
first start – look for `[migration] ...` lines in the console. Take a backup first:

```powershell
docker exec wishes-mysql mysqldump -uroot -proot wishes_sender > backup-v1.0.sql
```

To roll back: `Get-Content backup-v1.0.sql | docker exec -i wishes-mysql mysql -uroot -proot wishes_sender`, then run v1.0.0.
