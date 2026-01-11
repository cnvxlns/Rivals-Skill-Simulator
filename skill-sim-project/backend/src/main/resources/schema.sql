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

CREATE TABLE IF NOT EXISTS skill_effect (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    skill_id INTEGER NOT NULL,
    condition_key TEXT,
    logic_code TEXT,
    description TEXT,
    growth_pattern TEXT,
    FOREIGN KEY(skill_id) REFERENCES skill(id) ON DELETE CASCADE
);
