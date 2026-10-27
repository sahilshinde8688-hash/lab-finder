-- SmartSpace schema (SQLite). Safe to run repeatedly.
CREATE TABLE IF NOT EXISTS users (
  user_id              INTEGER PRIMARY KEY AUTOINCREMENT,
  full_name            TEXT    NOT NULL CHECK (length(trim(full_name)) > 0),
  institutional_email  TEXT    NOT NULL UNIQUE COLLATE NOCASE,
  password_hash        TEXT    NOT NULL,
  role                 TEXT    NOT NULL CHECK (role IN ('STUDENT','TEACHER','COORDINATOR')),
  department           TEXT,
  account_status       TEXT    NOT NULL DEFAULT 'ACTIVE' CHECK (account_status IN ('ACTIVE','DISABLED')),
  must_change_password INTEGER NOT NULL DEFAULT 0,
  failed_attempts      INTEGER NOT NULL DEFAULT 0,
  locked_until         TEXT,
  created_at           TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE TABLE IF NOT EXISTS rooms (
  room_id       INTEGER PRIMARY KEY AUTOINCREMENT,
  room_name     TEXT    NOT NULL UNIQUE COLLATE NOCASE,
  floor_number  INTEGER NOT NULL CHECK (floor_number BETWEEN 1 AND 7),
  room_type     TEXT    NOT NULL CHECK (room_type IN ('CLASSROOM','COMPUTER_LAB','LABORATORY','SEMINAR_ROOM')),
  capacity      INTEGER NOT NULL CHECK (capacity > 0),
  facilities    TEXT    NOT NULL DEFAULT '',
  active_status INTEGER NOT NULL DEFAULT 1 CHECK (active_status IN (0,1)),
  is_sample     INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS timetable (
  timetable_id     INTEGER PRIMARY KEY AUTOINCREMENT,
  room_id          INTEGER NOT NULL REFERENCES rooms(room_id),
  subject          TEXT    NOT NULL,
  class_name       TEXT    NOT NULL,
  faculty_id       INTEGER NOT NULL REFERENCES users(user_id),
  start_datetime   TEXT    NOT NULL,
  end_datetime     TEXT    NOT NULL,
  recurrence_rule  TEXT    NOT NULL DEFAULT 'NONE' CHECK (recurrence_rule IN ('NONE','WEEKLY')),
  recurrence_until TEXT,
  is_sample        INTEGER NOT NULL DEFAULT 0,
  CHECK (end_datetime > start_datetime),
  CHECK (recurrence_rule = 'NONE' OR recurrence_until IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS bookings (
  booking_id     INTEGER PRIMARY KEY AUTOINCREMENT,
  room_id        INTEGER NOT NULL REFERENCES rooms(room_id),
  user_id        INTEGER NOT NULL REFERENCES users(user_id),
  purpose        TEXT    NOT NULL,
  start_datetime TEXT    NOT NULL,
  end_datetime   TEXT    NOT NULL,
  booking_status TEXT    NOT NULL DEFAULT 'PENDING'
                 CHECK (booking_status IN ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED')),
  reviewed_by    INTEGER REFERENCES users(user_id),
  review_note    TEXT,
  created_at     TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime')),
  CHECK (end_datetime > start_datetime)
);

CREATE TABLE IF NOT EXISTS maintenance (
  maintenance_id     INTEGER PRIMARY KEY AUTOINCREMENT,
  room_id            INTEGER NOT NULL REFERENCES rooms(room_id),
  description        TEXT    NOT NULL,
  start_datetime     TEXT    NOT NULL,
  end_datetime       TEXT,
  maintenance_status TEXT    NOT NULL DEFAULT 'REPORTED'
                     CHECK (maintenance_status IN ('REPORTED','IN_PROGRESS','RESOLVED')),
  blocks_room        INTEGER NOT NULL DEFAULT 0 CHECK (blocks_room IN (0,1)),
  reported_by        INTEGER NOT NULL REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS notifications (
  notification_id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id         INTEGER NOT NULL REFERENCES users(user_id),
  title           TEXT    NOT NULL,
  message         TEXT    NOT NULL,
  read_status     INTEGER NOT NULL DEFAULT 0 CHECK (read_status IN (0,1)),
  created_at      TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%S','now','localtime'))
);

CREATE TABLE IF NOT EXISTS recent_views (
  user_id   INTEGER NOT NULL REFERENCES users(user_id),
  room_id   INTEGER NOT NULL REFERENCES rooms(room_id),
  viewed_at TEXT    NOT NULL,
  PRIMARY KEY (user_id, room_id)
);

CREATE TABLE IF NOT EXISTS floor_map_regions (
  floor_number INTEGER NOT NULL CHECK (floor_number IN (4,5)),
  region_key   TEXT    NOT NULL,
  map_number   TEXT    NOT NULL,
  map_type     TEXT    NOT NULL,
  x            REAL    NOT NULL CHECK (x >= 0 AND x <= 1),
  y            REAL    NOT NULL CHECK (y >= 0 AND y <= 1),
  width        REAL    NOT NULL CHECK (width > 0 AND x + width <= 1),
  height       REAL    NOT NULL CHECK (height > 0 AND y + height <= 1),
  room_id      INTEGER REFERENCES rooms(room_id),
  PRIMARY KEY (floor_number, region_key),
  UNIQUE (floor_number, room_id)
);

CREATE TABLE IF NOT EXISTS floor_map_region_drafts (
  floor_number INTEGER NOT NULL CHECK (floor_number IN (4,5)),
  region_key   TEXT    NOT NULL,
  map_number   TEXT    NOT NULL,
  map_type     TEXT    NOT NULL,
  x            REAL    NOT NULL CHECK (x >= 0 AND x <= 1),
  y            REAL    NOT NULL CHECK (y >= 0 AND y <= 1),
  width        REAL    NOT NULL CHECK (width > 0 AND x + width <= 1),
  height       REAL    NOT NULL CHECK (height > 0 AND y + height <= 1),
  room_id      INTEGER REFERENCES rooms(room_id),
  PRIMARY KEY (floor_number, region_key)
);

CREATE INDEX IF NOT EXISTS idx_rooms_floor        ON rooms(floor_number, active_status);
CREATE INDEX IF NOT EXISTS idx_timetable_room     ON timetable(room_id, start_datetime);
CREATE INDEX IF NOT EXISTS idx_timetable_faculty  ON timetable(faculty_id);
CREATE INDEX IF NOT EXISTS idx_bookings_room_time ON bookings(room_id, booking_status, start_datetime);
CREATE INDEX IF NOT EXISTS idx_bookings_user      ON bookings(user_id);
CREATE INDEX IF NOT EXISTS idx_maint_room         ON maintenance(room_id, maintenance_status);
CREATE INDEX IF NOT EXISTS idx_notif_user         ON notifications(user_id, read_status);
