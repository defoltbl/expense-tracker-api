package com.andrii

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object EmailVerificationTokensTable : Table("email_verification_tokens") {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(UsersTable.id)
    val token = varchar("token", length = 512).uniqueIndex()
    val expiresAt = datetime("expires_at")

    override val primaryKey = PrimaryKey(id)
}