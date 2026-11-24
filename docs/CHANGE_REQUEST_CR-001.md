# Change Request CR-001 – Database normalisation and data-integrity fixes

| Field | Value |
|---|---|
| Project | WishesSender v1 (CSE 224 DBMS Lab) |
| CR id | CR-001 |
| Target version | 1.1.0 |
| Date | 2026-11-24 |
| Type | Database design fix + backend/frontend changes |
| Priority | High (wrong-owner rows and duplicate birthdays are possible today) |
| Breaking change? | **No** – every API URL, request field and response field keeps working. Existing databases are upgraded automatically on start. |

---

## 1. Summary

A review of the v1 ERD found six design problems. All of them let the database store data that
is wrong or inconsistent, even though the app's normal screens rarely produce it. This CR fixes
each one **in the database first** (keys, foreign keys, CHECK constraints, lookup table), then makes
the Java backend use the new design and return clear `400/409` messages instead of SQL errors.

| # | Problem | Fix in one line |
|---|---|---|
| 1 | Redundant `user_id` in `wishes` and `notifications` | Drop the column; derive the owner through `connections` (and `events`). |
| 2 | Free-text categorical columns | CHECK constraints with the allowed values + backend validation. |
| 3 | `event_type` repeated in 3 tables, no lookup | New `event_types` table; FKs from `events`, `gifts`, `wish_templates`. |
| 4 | No value checks on numbers/flags | CHECK constraints on `gift_min/gift_max`, `yearly`, `days_before`, `price` (+ other flags). |
| 5 | Short `password_hash`, duplicate connections | `VARCHAR(255)`; `UNIQUE (user_id, name)` on `connections`. |
| 6 | Birthday stored twice (`connections.dob` + `events`) | `events` is the only place; `dob` is computed for the API. One birthday per connection enforced by a unique key. |

---

## 2. Change details

### Change 1 – Remove redundant `user_id` from `wishes` and `notifications`

**Problem.** `wishes.user_id` copies `connections.user_id`, and `notifications.user_id` copies
`events → connections.user_id`. Nothing makes the copies agree, so a row can say "user 5" while its
connection belongs to user 7 (a transitive dependency – breaks 3NF).

**Database changes**
- `wishes`: drop `user_id` and its foreign key. Owner = `wishes.connection_id → connections.user_id`.
- `notifications`: drop `user_id` and its foreign key. Owner = `notifications.event_id → events.connection_id → connections.user_id`.
- View `v_wishes_by_event_type` now joins `connections` to get `user_id`.
- Deleting a user still removes their wishes and notifications (cascade now runs `users → connections → events/wishes → notifications`).

**Backend changes**
- `WishController`: insert without `user_id`; history, "mark as sent" and "save" check ownership with a `JOIN connections`.
- `WishController.save`: **new rule** – if `eventId` is given it must belong to the same `connectionId` (before, a wish could point at another person's event). Returns `404` otherwise.
- `NotificationController`: list, unread count, mark read and read-all filter through `events → connections`.
- `ReminderScheduler`: the `INSERT IGNORE ... SELECT` no longer writes `user_id`.
- `DashboardController`: "wishes sent" and "recent wishes" counted through `connections`.

**Compatibility.** Responses still include `user_id` (taken from `connections`), so the JSON shape is unchanged.

---

### Change 2 – Allowed values for categorical columns

**Problem.** Columns such as `relation`, `religion`, `tone` and `status` are plain `VARCHAR`. The
dropdowns only offer valid values, but the API (or a typo in SQL) can store `"frend"` or `"sent "`,
which then breaks filters and reports.

**Allowed values (single list in `util/Rules.java`, same lists in the DB CHECKs)**

| Table.column | Allowed values | Null? |
|---|---|---|
| `users.role` | USER, ADMIN | no |
| `connections.relation` | Family, Friend, Partner, Colleague, Client, Customer | no |
| `connections.gender` | Female, Male, Other | yes |
| `connections.religion` | Islam, Hinduism, Christian, Buddhism, Other | yes |
| `festivals.religion` | All, Islam, Hinduism, Christian, Buddhism | no |
| `wish_templates.tone`, `wishes.tone` | Formal, Emotional, Funny, Romantic | wishes: yes |
| `wishes.language` | English, Bangla | yes |
| `wishes.source` | AI, TEMPLATE | no |
| `wishes.status` | SAVED, SENT | no |
| `gifts.category` | Books, Electronics, Home, Food, Flowers, Clothing, Experience, Travel, Art, Sports, Office, Other | no |

**Database changes:** named CHECK constraints `chk_users_role`, `chk_conn_relation`, `chk_conn_gender`,
`chk_conn_religion`, `chk_fest_religion`, `chk_tpl_tone`, `chk_wish_tone`, `chk_wish_language`,
`chk_wish_source`, `chk_wish_status`, `chk_gift_category`.

**Backend changes**
- New `Rules.oneOf(...)` helper: accepts any letter case (`"friend"` → `"Friend"`), returns
  `400 "relation must be one of: Family, Friend, ..."` for unknown values.
- Used in `ConnectionController`, `WishController.save`, `AdminController` (gifts, festivals).
- New read-only endpoint `GET /api/lookups` returns every allowed list (and the event types), so a
  client never has to hard-code them.

**Frontend changes:** Admin → gift *Category* becomes a dropdown (was free text).

**Deliberately left as free text:** `festivals.region`, `gifts.tag`, `connections.interests` – open
vocabularies (countries / interest words), not fixed categories.

---

### Change 3 – Shared `event_types` lookup table

**Problem.** `events.event_type`, `gifts.event_type` and `wish_templates.event_type` all hold the
same codes but are not linked to anything, so on the ERD `gifts` and `wish_templates` float alone and
a typo (`BIRTDAY`) silently stops gift and template matching.

**Database changes**
- New table `event_types (code PK, label, sort_order)` seeded with BIRTHDAY, ANNIVERSARY, FESTIVAL, CUSTOM.
- FKs: `fk_events_type`, `fk_tpl_type`, `fk_gifts_type` → `event_types(code)`.
- `gifts.event_type` becomes **nullable**: `NULL` means "suits any event". `'ANY'` is not an event
  type, so it is not put in the lookup table.

**Backend changes**
- `GiftController` and `AdminController` return `COALESCE(event_type, 'ANY') AS event_type`, and
  the admin "add gift" form still sends `ANY` → stored as `NULL`. The API looks exactly as before.
- `EventController.create` and `AdminController.addGift` check the code against `event_types` → `400` if unknown.

**ERD result:** `event_types` 1—N `events`, 1—N `gifts`, 1—N `wish_templates` – no isolated tables.

---

### Change 4 – Value checks on numbers and flags

| Constraint | Rule | Why |
|---|---|---|
| `chk_conn_gift_min` | `gift_min >= 0` | negative budget makes no sense |
| `chk_conn_gift_range` | `gift_max >= gift_min` | otherwise no gift can ever match |
| `chk_events_yearly` | `yearly IN (0,1)` | it is a boolean flag |
| `chk_notif_days` | `days_before BETWEEN 0 AND 30` | same range as the user setting |
| `chk_gift_price` | `price > 0` | free/negative gifts break budget matching |
| `chk_users_remind` | `remind_days_before BETWEEN 0 AND 30` | source of `days_before` |
| `chk_users_ai`, `chk_users_share`, `chk_notif_read` | `IN (0,1)` | boolean flags |

**Backend changes:** `Rules.range(...)` / `Rules.flag(...)` give friendly `400` messages before the DB is
reached (e.g. *"Gift budget max must be greater than or equal to min"*). A global handler
(`ApiErrorHandler`) turns any remaining DB constraint error into `400`/`409` with a readable message
instead of `500`. `Db.num` returns `400` (not `500`) for non-numeric input.

**Frontend changes:** `min="0"` on budget and price inputs.

---

### Change 5 – Small design gaps

**5a. `password_hash` length.** `VARCHAR(100)` → `VARCHAR(255)`. BCrypt hashes are 60 chars, but an
argon2 hash with its parameters is ~100 chars and would be cut off. No hashing change now – this only
makes a future switch possible without another migration.

**5b. Duplicate connections.** New `UNIQUE KEY uq_conn_user_name (user_id, name)`
(case-insensitive by MySQL collation).
- `ConnectionController` create/update returns `409 "You already have a connection named Rina"`.
- Two different people with the same name can be told apart with a suffix, e.g. "Rahul (Office)".
- Different users can still both have a "Rahul".

---

### Change 6 – Birthday stored only once

**Problem.** `connections.dob` and the auto-created BIRTHDAY row in `events` hold the same fact.
Editing the date of birth did **not** update the event, so the profile and the reminders could show
different birthdays, and the "Add event" form could create a second birthday.

**Database changes**
- `connections.dob` column is removed – the BIRTHDAY event is the single source of truth.
- `events.birthday_of` – a virtual generated column (`connection_id` when type is BIRTHDAY, else `NULL`)
  with `UNIQUE KEY uq_one_birthday` → **at most one birthday per connection**, enforced by MySQL.

**Backend changes**
- `ConnectionController` returns `dob` computed with a `LEFT JOIN events ... event_type = 'BIRTHDAY'`
  – same field name and format as before.
- Create/update with `dob` → inserts or updates the birthday event. Clearing `dob` → removes it.
- `EventController.create` with type BIRTHDAY when one exists → `409` *"... already has a birthday.
  Edit the date of birth on their profile instead."* Birthdays are always yearly.
- Deleting the birthday event clears the date of birth (they can no longer disagree).

---

## 3. Data migration (existing databases)

`schema.sql` only creates missing tables, so a new `SchemaMigrator` runs once at start-up, after
`schema.sql`/`data.sql` and before the web server and scheduler. Every step checks
`information_schema` first, so it is **safe to run on every start** and does nothing on a new DB.

| Step | What it does with existing rows |
|---|---|
| 1 | Widen `password_hash` to 255. |
| 2 | Drop `user_id` (and its FK) from `wishes` and `notifications`. |
| 3 | Normalise event types (upper-case; unknown → CUSTOM; gifts `ANY`/unknown → NULL); add the 3 FKs. |
| 4 | Copy each `connections.dob` into its birthday event (insert if missing, update date if different – the profile value wins because it is what the user last edited). |
| 5 | If a person has several BIRTHDAY events, keep the oldest; the others become CUSTOM events (nothing deleted). Add `birthday_of` + unique key. Drop `connections.dob`. |
| 6 | Rename duplicate connection names to `Name (2)`, `Name (3)`…; add the unique key. |
| 7 | Fix out-of-range values (e.g. swap `gift_min/gift_max`, unknown relation → Friend, unknown gender/religion → NULL, unknown category → Other); add each CHECK constraint. |

Every change is printed in the console as `[migration] ...`.

---

## 4. Files affected

| Area | Files |
|---|---|
| DB | `schema.sql`, `data.sql` |
| Backend (new) | `config/SchemaMigrator.java`, `util/Rules.java`, `util/ApiErrorHandler.java`, `controller/LookupController.java` |
| Backend (changed) | `ConnectionController`, `EventController`, `WishController`, `NotificationController`, `ReminderScheduler`, `DashboardController`, `GiftController`, `AdminController`, `SettingsController`, `util/Db.java` |
| Frontend | `admin.html` (category dropdown, error toasts), `person.html` (error toasts on add event / festival), `people.html` (`min="0"`) |
| Docs | `CHANGELOG.md`, `README.md`, this CR |

## 5. API compatibility

| Endpoint | Before | After |
|---|---|---|
| All URLs / request bodies | – | unchanged |
| `GET /api/connections[/id]` | `dob` from column | `dob` computed – same name/format |
| `GET /api/notifications`, `/api/wishes` | `user_id` from own column | `user_id` from `connections` – same name |
| Gifts (`suggest`, admin list) | `event_type: "ANY"` | still `"ANY"` (NULL in DB) |
| New | – | `GET /api/lookups` |
| Invalid input | stored anyway or `500` | `400` / `409` with a message |

## 6. Test plan

1. **Fresh DB:** start on an empty MySQL → all tables, views, constraints created; full smoke test passes.
2. **Upgrade DB:** create data with v1.0.0 (incl. duplicate names, `dob` ≠ birthday event, two birthday
   events, `ANY` gifts), start v1.1.0 → migration log, data preserved, constraints present.
3. **Second start** on the upgraded DB → no migration steps run, no errors.
4. **Negative tests:** bad relation, `gift_max < gift_min`, duplicate name, second birthday, wish with
   another person's event, gift price 0, `remind_days_before = 99` → `400/404/409`, nothing stored.
5. **UI:** every page loads with no console errors; Wish Studio with Gemini and with template fallback.

**Results (2026-11-24, MySQL 8.0 in Docker, Java 8):**

| Run | Result |
|---|---|
| Upgrade of a v1.0.0 DB (duplicate name, `dob` ≠ event, 2 birthdays, swapped budget, `ANY` gifts, custom category) | 34 migration steps, all data kept; API suite **78/78 passed** |
| Second start on the upgraded DB | 0 migration steps, no errors |
| Fresh empty DB | 0 migration steps, 20 CHECKs + FKs created, seed data complete; API suite **71/71 passed** |
| Raw SQL bypassing the app (bad relation, max < min, price 0, `ANY`, 2nd birthday, `yearly = 2`, `days_before = 45`, duplicate name, role `ROOT`, status `DONE`) | all rejected by MySQL |
| Browser: all 10 pages, admin category dropdown, "already has a birthday" toast | no console errors (except the deliberate 409) |
| Gemini | Template fallback tested (no key in the test run). `GeminiService` code is unchanged. |

## 7. Rollback

Back up first: `docker exec wishes-mysql mysqldump -uroot -proot wishes_sender > backup.sql`.
To roll back, restore the dump and run v1.0.0. (v1.0.0 cannot run on an upgraded DB because
`connections.dob` and the `user_id` columns are gone.)
