-- 초기 스킬 목록을 애플리케이션 시작 시 삽입하는 SQL 스크립트
INSERT INTO skill (name, tier, grade, description, weight) VALUES
('Record Holder', 'GOLD', 'S', 'Extends record-holding ability; boosts stamina and control.', 5),
('Bullpen Ace', 'GOLD', 'A', 'Thrives in relief situations; improves clutch pitching.', 4),
('Defensive Wall', 'SILVER', 'B', 'Reinforces defensive positioning and reaction time.', 3),
('Speed Demon', 'BRONZE', 'A', 'Increases base-running speed and agility.', 2),
('Tactical Mind', 'IRON', 'C', 'Smarter pitch selection and batter analysis.', 1),
('Field General', 'SILVER', 'B', 'Leads the defense with improved awareness.', 2),
('Clutch Hitter', 'GOLD', 'A', 'Boosts contact in high-pressure at-bats.', 3),
('Moment Maker', 'MOMENT', 'S', 'Signature moment-tier skill for special lock rule.', 1);
