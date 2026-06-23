package com.andrii

import org.jetbrains.exposed.sql.Table

object ExpensesTable : Table("expenses") {
    val id = integer("id").autoIncrement()
    val description = varchar("description", length = 255)
    val amount = double("amount")
    val category = varchar("category", length = 100)
    val date = varchar("date", length = 20)
    val userId = integer("user_id")

    override val primaryKey = PrimaryKey(id)
}