CREATE TABLE IF NOT EXISTS skill (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    position TEXT,
    sub_positions TEXT,
    level TEXT,
    tier TEXT NOT NULL,
    description TEXT,
    weight INTEGER
);
