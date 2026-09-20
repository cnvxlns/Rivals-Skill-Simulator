package com.example.skillsim.repository

import com.example.skillsim.model.PositionTraining
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

/**
 * 구단의 포지션 훈련 현황. 계정당 한 벌뿐이라 사용자 번호가 곧 기본키다.
 */
interface PositionTrainingRepository {

    /** 저장한 적이 없으면 null이다. 호출자가 빈 설정으로 읽는다. */
    fun find(userId: Long): PositionTraining?

    fun save(userId: Long, training: PositionTraining): PositionTraining
}

@Repository
class JdbcPositionTrainingRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : PositionTrainingRepository {

    override fun find(userId: Long): PositionTraining? = jdbc.query(
        "select body from position_training where user_id = :userId",
        mapOf("userId" to userId),
    ) { rs, _ -> objectMapper.readValue(rs.getString("body"), PositionTraining::class.java) }
        .firstOrNull()

    override fun save(userId: Long, training: PositionTraining): PositionTraining {
        jdbc.update(
            """
            insert into position_training (user_id, body_version, body)
            values (:userId, :bodyVersion, cast(:body as jsonb))
            on conflict (user_id) do update
            set body_version = excluded.body_version,
                body = excluded.body,
                updated_at = now()
            """.trimIndent(),
            mapOf(
                "userId" to userId,
                "bodyVersion" to PositionTraining.BODY_VERSION,
                "body" to objectMapper.writeValueAsString(training),
            ),
        )
        return training
    }
}
