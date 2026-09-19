-- 이 프로젝트의 첫 쓰기 경로다.
--
-- 스킬 데이터(score_skills.csv 등)는 여전히 클래스패스 CSV가 원천이며 DB에 넣지 않는다.
-- 여기에는 사용자가 만든 것 — 계정과 덱 — 만 들어간다.

create table users (
    id            bigserial   primary key,
    email         text        not null,
    password_hash text        not null,
    -- 토큰 일괄 무효화 스위치. 발급 시 클레임에 싣고 검증할 때 대조한다.
    -- 이 값을 올리면 그 사용자에게 발급된 토큰이 전부 죽는다.
    token_version integer     not null default 0,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    constraint users_email_unique unique (email),
    -- 서비스가 항상 소문자로 정규화해 넣는다. 대소문자만 다른 중복 계정을 막는다.
    constraint users_email_lowercase check (email = lower(email)),
    constraint users_email_shape check (email like '%_@_%._%')
);

create table decks (
    id           bigserial   primary key,
    user_id      bigint      not null references users (id) on delete cascade,
    name         text        not null,
    -- body의 스키마 버전. 로스터 모양을 바꿀 때 읽는 쪽이 판별할 수 있어야 한다.
    body_version smallint    not null,
    -- 26명 로스터 전체. 항상 통째로 읽고 통째로 쓰므로 행으로 펼치지 않는다.
    -- skill_id에 외래키를 걸지 않는 이유는 스킬 원천이 DB가 아니라 CSV라 걸 대상이
    -- 없기 때문이다. 실재 여부는 저장 전에 서비스가 확인한다.
    body         jsonb       not null,
    -- 목록 화면용 캐시다. 채점 기준(가중치·조건확률)이 바뀌면 낡을 수 있으므로
    -- 상세 조회는 항상 다시 계산한다. 순위나 비교의 근거로 쓰지 않는다.
    total_score  numeric(12, 2),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    constraint decks_name_length check (length(btrim(name)) between 1 and 50),
    constraint decks_name_unique_per_user unique (user_id, name),
    -- 애플리케이션 검증이 뚫려도 26명이 아닌 덱은 저장되지 않게 하는 마지막 방어선.
    constraint decks_roster_size check (
        jsonb_typeof(body -> 'players') = 'array'
        and jsonb_array_length(body -> 'players') = 26
    )
);

create index decks_owner_recent_idx on decks (user_id, updated_at desc);
