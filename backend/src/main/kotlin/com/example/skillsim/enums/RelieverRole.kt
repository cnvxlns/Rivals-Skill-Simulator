package com.example.skillsim.enums

/**
 * 중간 계투의 하위 역할.
 *
 * 게임의 투수 전략 화면이 중계 정원을 이 셋으로 나눠 배분한다.
 *
 * 교체 전술의 능력치 버프와 워크북의 보직 조건에 사용한다.
 */
enum class RelieverRole {
    /** 승리조. */
    WIN,

    /** 추격조. */
    CHASE,

    /** 롱 릴리프. */
    LONG,
}
