-- =========================================================
--  WishesSender - MySQL schema (runs automatically on start)
--  v1.1: lookup table for event types, CHECK constraints,
--        no redundant user_id / dob columns (see CHANGELOG.md)
--  Old v1.0 databases are upgraded by config/SchemaMigrator.java
-- =========================================================

-- 1. Users (regular users and admins)
CREATE TABLE IF NOT EXISTS users (
    id                 INT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    email              VARCHAR(150) NOT NULL UNIQUE,
    password_hash      VARCHAR(255) NOT NULL,                  -- 255 = room for bcrypt or argon2
    role               VARCHAR(10)  NOT NULL DEFAULT 'USER',
    ai_enabled         TINYINT(1)   NOT NULL DEFAULT 1,        -- privacy: may we use AI?
    share_details      TINYINT(1)   NOT NULL DEFAULT 1,        -- privacy: send interests to AI?
    remind_days_before INT          NOT NULL DEFAULT 1,        -- 0 = only on the day
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_users_role   CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_ai     CHECK (ai_enabled IN (0, 1)),
    CONSTRAINT chk_users_share  CHECK (share_details IN (0, 1)),
    CONSTRAINT chk_users_remind CHECK (remind_days_before BETWEEN 0 AND 30)
);

-- 2. Connections (the people a user cares about)
--    The date of birth is NOT stored here: it is the BIRTHDAY row in events.
CREATE TABLE IF NOT EXISTS connections (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT          NOT NULL,
    name           VARCHAR(100) NOT NULL,
    relation       VARCHAR(20)  NOT NULL,
    phone          VARCHAR(30),
    email          VARCHAR(150),
    address        VARCHAR(255),
    gender         VARCHAR(10),
    religion       VARCHAR(20),
    favourite_dish VARCHAR(100),
    interests      VARCHAR(255),                   -- comma separated, e.g. "books,music"
    gift_min       INT NOT NULL DEFAULT 0,
    gift_max       INT NOT NULL DEFAULT 5000,
    notes          TEXT,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_conn_user (user_id),
    INDEX idx_conn_name (name),
    UNIQUE KEY uq_conn_user_name (user_id, name),  -- the same person cannot be added twice
    CONSTRAINT chk_conn_relation   CHECK (relation IN ('Family', 'Friend', 'Partner', 'Colleague', 'Client', 'Customer')),
    CONSTRAINT chk_conn_gender     CHECK (gender IS NULL OR gender IN ('Female', 'Male', 'Other')),
    CONSTRAINT chk_conn_religion   CHECK (religion IS NULL OR religion IN ('Islam', 'Hinduism', 'Christian', 'Buddhism', 'Other')),
    CONSTRAINT chk_conn_gift_min   CHECK (gift_min >= 0),
    CONSTRAINT chk_conn_gift_range CHECK (gift_max >= gift_min)
);

-- 3. Event types (lookup table shared by events, gifts and wish_templates)
CREATE TABLE IF NOT EXISTS event_types (
    code       VARCHAR(20) PRIMARY KEY,            -- BIRTHDAY, ANNIVERSARY, FESTIVAL, CUSTOM
    label      VARCHAR(50) NOT NULL,
    sort_order INT         NOT NULL DEFAULT 0
);

-- 4. Events (one connection has many events)
CREATE TABLE IF NOT EXISTS events (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    connection_id INT          NOT NULL,
    title         VARCHAR(100) NOT NULL,
    event_type    VARCHAR(20)  NOT NULL,
    event_date    DATE         NOT NULL,
    yearly        TINYINT(1)   NOT NULL DEFAULT 1, -- repeats every year?
    description   VARCHAR(255),
    -- = connection_id for a birthday, NULL otherwise. The UNIQUE key on it
    --   allows only ONE birthday per connection (NULLs never clash).
    birthday_of   INT AS (IF(event_type = 'BIRTHDAY', connection_id, NULL)) VIRTUAL,
    FOREIGN KEY (connection_id) REFERENCES connections(id) ON DELETE CASCADE,
    CONSTRAINT fk_events_type FOREIGN KEY (event_type) REFERENCES event_types(code),
    INDEX idx_event_date (event_date),
    UNIQUE KEY uq_one_birthday (birthday_of),
    CONSTRAINT chk_events_yearly CHECK (yearly IN (0, 1))
);

-- 5. Festival master list (managed by admin)
CREATE TABLE IF NOT EXISTS festivals (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    festival_date DATE         NOT NULL,
    religion      VARCHAR(20)  NOT NULL DEFAULT 'All',
    region        VARCHAR(50)  NOT NULL DEFAULT 'Bangladesh',
    CONSTRAINT chk_fest_religion CHECK (religion IN ('All', 'Islam', 'Hinduism', 'Christian', 'Buddhism'))
);

-- 6. In-app notifications created by the reminder scheduler
--    The owner is found through event -> connection -> user (no user_id copy).
CREATE TABLE IF NOT EXISTS notifications (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    event_id    INT          NOT NULL,
    for_date    DATE         NOT NULL,             -- the date of the event occurrence
    days_before INT          NOT NULL,
    message     VARCHAR(255) NOT NULL,
    is_read     TINYINT(1)   NOT NULL DEFAULT 0,
    -- 1 = removed from the reminders list ("Mark all read" or a wish was sent for the event).
    -- The row is kept (not deleted) so the UNIQUE key stops the scheduler from creating it again.
    is_cleared  TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    UNIQUE KEY uq_notification (event_id, for_date, days_before),  -- never remind twice
    CONSTRAINT chk_notif_days    CHECK (days_before BETWEEN 0 AND 30),
    CONSTRAINT chk_notif_read    CHECK (is_read IN (0, 1)),
    CONSTRAINT chk_notif_cleared CHECK (is_cleared IN (0, 1))
);

-- 7. Wish templates (used when AI is off or fails)
CREATE TABLE IF NOT EXISTS wish_templates (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(20) NOT NULL,
    tone       VARCHAR(20) NOT NULL,
    text       TEXT        NOT NULL,                -- {name} is replaced with the person's name
    CONSTRAINT fk_tpl_type FOREIGN KEY (event_type) REFERENCES event_types(code),
    CONSTRAINT chk_tpl_tone CHECK (tone IN ('Formal', 'Emotional', 'Funny', 'Romantic'))
);

-- 8. Wish history (the owner is found through connection -> user)
CREATE TABLE IF NOT EXISTS wishes (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    connection_id INT         NOT NULL,
    event_id      INT,
    tone          VARCHAR(20),
    language      VARCHAR(20),
    text          TEXT        NOT NULL,
    source        VARCHAR(10) NOT NULL,
    status        VARCHAR(10) NOT NULL DEFAULT 'SAVED',
    created_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (connection_id) REFERENCES connections(id) ON DELETE CASCADE,
    FOREIGN KEY (event_id)      REFERENCES events(id)      ON DELETE SET NULL,
    CONSTRAINT chk_wish_tone     CHECK (tone IS NULL OR tone IN ('Formal', 'Emotional', 'Funny', 'Romantic')),
    CONSTRAINT chk_wish_language CHECK (language IS NULL OR language IN ('English', 'Bangla')),
    CONSTRAINT chk_wish_source   CHECK (source IN ('AI', 'TEMPLATE')),
    CONSTRAINT chk_wish_status   CHECK (status IN ('SAVED', 'SENT'))
);

-- 9. Gift catalog (managed by admin)
CREATE TABLE IF NOT EXISTS gifts (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    category   VARCHAR(50)  NOT NULL,
    price      INT          NOT NULL,               -- in BDT
    event_type VARCHAR(20),                         -- NULL = suits any event (shown as "ANY")
    tag        VARCHAR(50)  NOT NULL,               -- matched against connection interests
    reason     VARCHAR(255) NOT NULL,               -- "Why this gift?"
    CONSTRAINT fk_gifts_type FOREIGN KEY (event_type) REFERENCES event_types(code),
    CONSTRAINT chk_gift_price CHECK (price > 0),
    CONSTRAINT chk_gift_category CHECK (category IN ('Books', 'Electronics', 'Home', 'Food', 'Flowers', 'Clothing',
                                                     'Experience', 'Travel', 'Art', 'Sports', 'Office', 'Other'))
);

-- =========================================================
--  VIEWS (used for reminders, dashboard and reports)
--  (they only use columns that exist in both v1.0 and v1.1,
--   so they can be created before the migrator runs)
-- =========================================================

-- Every event with its NEXT occurrence date and days left.
-- A yearly event (birthday) on 1990-05-10 has next_date = this year's or next year's 05-10.
CREATE OR REPLACE VIEW v_event_next AS
SELECT t.*, DATEDIFF(t.next_date, CURDATE()) AS days_left
FROM (
    SELECT e.id, e.connection_id, e.title, e.event_type, e.event_date, e.yearly, e.description,
           c.user_id, c.name AS connection_name, c.relation,
           CASE WHEN e.yearly = 1 THEN
                DATE_ADD(e.event_date, INTERVAL
                    (YEAR(CURDATE()) - YEAR(e.event_date)
                     + IF(DATE_FORMAT(e.event_date, '%m%d') < DATE_FORMAT(CURDATE(), '%m%d'), 1, 0)) YEAR)
           ELSE e.event_date END AS next_date
    FROM events e
    JOIN connections c ON c.id = e.connection_id
) t;

-- Report: connections per relation type
CREATE OR REPLACE VIEW v_connections_by_relation AS
SELECT user_id, relation, COUNT(*) AS total
FROM connections
GROUP BY user_id, relation;

-- Report: events per month (by the month the event falls in)
CREATE OR REPLACE VIEW v_events_per_month AS
SELECT c.user_id, MONTH(e.event_date) AS month_no, COUNT(*) AS total
FROM events e
JOIN connections c ON c.id = e.connection_id
GROUP BY c.user_id, MONTH(e.event_date);

-- Report: wishes by event type (festival, birthday, ...). The owner comes from connections.
CREATE OR REPLACE VIEW v_wishes_by_event_type AS
SELECT c.user_id, COALESCE(e.event_type, 'GENERAL') AS event_type, COUNT(*) AS total
FROM wishes w
JOIN connections c ON c.id = w.connection_id
LEFT JOIN events e ON e.id = w.event_id
GROUP BY c.user_id, COALESCE(e.event_type, 'GENERAL');
