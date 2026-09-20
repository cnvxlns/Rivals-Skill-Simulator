-- 구단의 포지션 훈련(포훈) 현황.
--
-- 게임에서 포훈은 선수가 아니라 자리에 붙고 모든 라인업에 공통으로 걸린다. 그래서 덱 본문에
-- 넣지 않고 계정당 한 벌만 둔다. 사용자 번호가 곧 기본키다.
--
-- 레벨이 아니라 능력치 증가치를 그대로 담는다. 레벨별 수치표가 공개된 적이 없어 우리가
-- 표를 흉내 내면 틀린 값을 퍼뜨리게 된다.

create table position_training (
    user_id      bigint      primary key references users (id) on delete cascade,
    -- body의 스키마 버전. 모양을 바꿀 때 읽는 쪽이 판별할 수 있어야 한다.
    body_version smallint    not null,
    -- 자리별 능력치 증가치와 스킬 레벨 보너스. 항상 통째로 읽고 쓴다.
    -- skill_id에 외래키를 걸지 않는 이유는 decks.body와 같다. 스킬 원천이 CSV라 걸 대상이 없다.
    body         jsonb       not null,
    updated_at   timestamptz not null default now(),
    constraint position_training_slots_object check (
        jsonb_typeof(body -> 'slots') = 'object'
    )
);
