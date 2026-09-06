package com.example.skillsim.repository

import com.example.skillsim.model.Deck
import com.example.skillsim.model.DeckRoster
import com.example.skillsim.model.DeckSummary
import com.fasterxml.jackson.databind.ObjectMapper
import java.sql.ResultSet
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcDeckRepository(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper,
) : DeckRepository {

    override fun findSummaries(userId: Long): List<DeckSummary> = jdbc.query(
        """
        select id, name, total_score, updated_at
        from decks
        where user_id = :userId
        order by updated_at desc
        """.trimIndent(),
        mapOf("userId" to userId),
    ) { rs, _ ->
        DeckSummary(
            id = rs.getLong("id"),
            name = rs.getString("name"),
            totalScore = rs.getBigDecimal("total_score")?.toDouble(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
    }

    override fun findByIdAndUser(id: Long, userId: Long): Deck? = jdbc.query(
        "select $COLUMNS from decks where id = :id and user_id = :userId",
        mapOf("id" to id, "userId" to userId),
        mapper(),
    ).firstOrNull()

    override fun countByUser(userId: Long): Int = jdbc.queryForObject(
        "select count(*) from decks where user_id = :userId",
        mapOf("userId" to userId),
        Int::class.java,
    ) ?: 0

    override fun create(userId: Long, name: String, roster: DeckRoster, totalScore: Double): Deck =
        jdbc.query(
            """
            insert into decks (user_id, name, body_version, body, total_score)
            values (:userId, :name, :bodyVersion, cast(:body as jsonb), :totalScore)
            returning $COLUMNS
            """.trimIndent(),
            params(userId, name, roster, totalScore),
            mapper(),
        ).first()

    override fun update(
        id: Long,
        userId: Long,
        name: String,
        roster: DeckRoster,
        totalScore: Double,
    ): Deck? = jdbc.query(
        """
        update decks
        set name = :name,
            body_version = :bodyVersion,
            body = cast(:body as jsonb),
            total_score = :totalScore,
            updated_at = now()
        where id = :id and user_id = :userId
        returning $COLUMNS
        """.trimIndent(),
        params(userId, name, roster, totalScore) + mapOf("id" to id),
        mapper(),
    ).firstOrNull()

    override fun delete(id: Long, userId: Long): Boolean = jdbc.update(
        "delete from decks where id = :id and user_id = :userId",
        mapOf("id" to id, "userId" to userId),
    ) > 0

    // 코틀린 String을 jsonb 컬럼에 그대로 넣으면 타입이 맞지 않는다. SQL에서 명시적으로 캐스팅한다.
    private fun params(userId: Long, name: String, roster: DeckRoster, totalScore: Double) = mapOf(
        "userId" to userId,
        "name" to name,
        "bodyVersion" to Deck.BODY_VERSION,
        "body" to objectMapper.writeValueAsString(roster),
        "totalScore" to totalScore,
    )

    private fun mapper() = RowMapper { rs: ResultSet, _: Int ->
        Deck(
            id = rs.getLong("id"),
            userId = rs.getLong("user_id"),
            name = rs.getString("name"),
            roster = objectMapper.readValue(rs.getString("body"), DeckRoster::class.java),
            totalScore = rs.getBigDecimal("total_score")?.toDouble(),
            createdAt = rs.getTimestamp("created_at").toInstant(),
            updatedAt = rs.getTimestamp("updated_at").toInstant(),
        )
    }

    private companion object {
        const val COLUMNS = "id, user_id, name, body, total_score, created_at, updated_at"
    }
}
