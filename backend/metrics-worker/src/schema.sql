-- One row per install per day: aggregate counts only. No IP, no user agent, no time of day.
CREATE TABLE IF NOT EXISTS daily (
  install_id    TEXT NOT NULL,  -- random UUID made on the phone when the family opted in
  day           TEXT NOT NULL,  -- YYYY-MM-DD, the phone's local day
  tester_source TEXT NOT NULL,  -- unset | family | friend | stranger
  app_version   TEXT NOT NULL,
  lang          TEXT NOT NULL,  -- mr | hi | en | other
  counts        TEXT NOT NULL,  -- JSON object of whole numbers under fixed keys
  received_on   TEXT NOT NULL,  -- YYYY-MM-DD
  PRIMARY KEY (install_id, day)
);
CREATE INDEX IF NOT EXISTS daily_day ON daily (day);
