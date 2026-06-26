package com.andrii

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

data class User(val id: Int, val username: String, val passwordHash: String)

@kotlinx.serialization.Serializable
data class LoginRequest(val username: String, val password: String)

@kotlinx.serialization.Serializable
data class LoginResponse(val token: String)

@kotlinx.serialization.Serializable
data class RegisterRequest(val username: String, val password: String)

object UserRepository {

    fun findByUsername(username: String): User? {
        return transaction {
            UsersTable.selectAll().where { UsersTable.username eq username }
                .map {
                    User(
                        id = it[UsersTable.id],
                        username = it[UsersTable.username],
                        passwordHash = it[UsersTable.passwordHash]
                    )
                }.singleOrNull()
        }
    }

    fun createUser(username: String, plainPassword: String): User? {
        val existingUser = findByUsername(username)
        if (existingUser != null) {
            return null // username already taken
        }

        val hashedPassword = hashPassword(plainPassword)
        val insertedId = transaction {
            UsersTable.insert {
                it[UsersTable.username] = username
                it[UsersTable.passwordHash] = hashedPassword
            } get UsersTable.id
        }

        return User(id = insertedId, username = username, passwordHash = hashedPassword)
    }

    fun hashPassword(plainPassword: String): String {
        return BCrypt.withDefaults().hashToString(12, plainPassword.toCharArray())
    }

    fun verifyPassword(plainPassword: String, hash: String): Boolean {
        val result = BCrypt.verifyer().verify(plainPassword.toCharArray(), hash)
        return result.verified
    }
}