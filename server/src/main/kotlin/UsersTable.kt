package com.andrii

import org.jetbrains.exposed.sql.Table

object UsersTable : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", length = 50).uniqueIndex()
    val email = varchar("email", length = 255).uniqueIndex()
    val passwordHash = varchar("password_hash", length = 255)
    val verified = bool("verified").default(false)

    override val primaryKey = PrimaryKey(id)
}