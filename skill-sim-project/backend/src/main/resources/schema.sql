DROP TABLE IF EXISTS skill_effect;
DROP TABLE IF EXISTS skill;
DROP TABLE IF EXISTS score_effect;
DROP TABLE IF EXISTS score_skill;

CREATE TABLE IF NOT EXISTS score_skill (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    skill_key TEXT NOT NULL UNIQUE,
    card_type TEXT NOT NULL,
    position TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS score_effect (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    score_skill_id INTEGER NOT NULL,
    stat TEXT NOT NULL,
    condition_key TEXT NOT NULL,
    effect_values TEXT NOT NULL,
    base_stat TEXT,
    FOREIGN KEY(score_skill_id) REFERENCES score_skill(id) ON DELETE CASCADE
);
