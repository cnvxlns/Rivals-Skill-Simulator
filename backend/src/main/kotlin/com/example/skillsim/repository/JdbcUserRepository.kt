package com.example.skillsim.repository

import com.example.skillsim.model.User
import java.sql.ResultSet
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcUserRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : UserRepository {

    override fun findByEmail(email: String): User? = jdbc.query(
        "select $COLUMNS from users where email = :email",
        mapOf("email" to email),
        MAPPER,
    ).firstOrNull()

    override fun findById(id: Long): User? = jdbc.query(
        "select $COLUMNS from users where id = :id",
        mapOf("id" to id),
        MAPPER,
    ).firstOrNull()

    override fun create(email: String, passwordHash: String): User = jdbc.query(
        """
        insert into users (email, password_hash)
        values (:email, :passwordHash)
        returning $COLUMNS
        """.trimIndent(),
        mapOf("email" to email, "passwordHash" to passwordHash),
        MAPPER,
    ).first()

    private companion object {
        const val COLUMNS = "id, email, password_hash, token_version, created_at"

        val MAPPER = RowMapper { rs: ResultSet, _: Int ->
            User(
                id = rs.getLong("id"),
                email = rs.getString("email"),
                passwordHash = rs.getString("password_hash"),
                tokenVersion = rs.getInt("token_version"),
                createdAt = rs.getTimestamp("created_at").toInstant(),
            )
        }
    }
}
