package com.andrii

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

data class User(val id: Int, val username: String, val email: String, val passwordHash: String)

@kotlinx.serialization.Serializable
data class LoginRequest(val username: String, val password: String)

@kotlinx.serialization.Serializable
data class RefreshRequest(val refreshToken: String)

@kotlinx.serialization.Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String)


@kotlinx.serialization.Serializable
data class RegisterRequest(val username: String, val email: String, val password: String)

@kotlinx.serialization.Serializable
data class ForgotPasswordRequest(val username: String)

@kotlinx.serialization.Serializable
data class ResetPasswordRequest(val resetToken: String, val newPassword: String)

object UserRepository {

    fun findByUsername(username: String): User? {
        return transaction {
            UsersTable.selectAll().where { UsersTable.username eq username }
                .map {
                    User(
                        id = it[UsersTable.id],
                        username = it[UsersTable.username],
                        email = it[UsersTable.email],
                        passwordHash = it[UsersTable.passwordHash]
                    )
                }.singleOrNull()
        }
    }

    fun createUser(username: String, email: String, plainPassword: String): User? {
        val existingUser = findByUsername(username)
        if (existingUser != null) {
            return null
        }

        val hashedPassword = hashPassword(plainPassword)
        val insertedId = transaction {
            UsersTable.insert {
                it[UsersTable.username] = username
                it[UsersTable.email] = email
                it[UsersTable.passwordHash] = hashedPassword
            } get UsersTable.id
        }

        return User(id = insertedId, username = username, email = email, passwordHash = hashedPassword)
    }

    fun hashPassword(plainPassword: String): String {
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray())
    }

    fun verifyPassword(plainPassword: String, hash: String): Boolean {
        val result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hash)
        return result.verified
    }
}