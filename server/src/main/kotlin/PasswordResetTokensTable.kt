package com.andrii

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object PasswordResetTokensTable : Table("password_reset_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id")
    val token = varchar("token", length = 512).uniqueIndex()
    val expiresAt = datetime("expires_at")

    override val primaryKey = PrimaryKey(id)
}