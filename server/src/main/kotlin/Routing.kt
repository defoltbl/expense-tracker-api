package com.andrii

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Application.configureRouting() {
    routing {

        // CREATE
        post("/expenses") {
            val newExpense = call.receive<Expense>()
            val insertedId = transaction {
                ExpensesTable.insert {
                    it[description] = newExpense.description
                    it[amount] = newExpense.amount
                    it[category] = newExpense.category
                    it[date] = newExpense.date
                    it[userId] = newExpense.userId
                } get ExpensesTable.id
            }
            val created = newExpense.copy(id = insertedId)
            call.respond(HttpStatusCode.Created, created)
        }

        // READ all
        get("/expenses") {
            val allExpenses = transaction {
                ExpensesTable.selectAll().map {
                    Expense(
                        id = it[ExpensesTable.id],
                        description = it[ExpensesTable.description],
                        amount = it[ExpensesTable.amount],
                        category = it[ExpensesTable.category],
                        date = it[ExpensesTable.date],
                        userId = it[ExpensesTable.userId]
                    )
                }
            }
            call.respond(allExpenses)
        }

        // READ one
        get("/expenses/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@get
            }
            val expense = transaction {
                ExpensesTable.selectAll().where { ExpensesTable.id eq id }
                    .map {
                        Expense(
                            id = it[ExpensesTable.id],
                            description = it[ExpensesTable.description],
                            amount = it[ExpensesTable.amount],
                            category = it[ExpensesTable.category],
                            date = it[ExpensesTable.date],
                            userId = it[ExpensesTable.userId]
                        )
                    }.singleOrNull()
            }
            if (expense != null) {
                call.respond(expense)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // UPDATE
        put("/expenses/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@put
            }
            val updatedExpense = call.receive<Expense>()
            val rowsUpdated = transaction {
                ExpensesTable.update({ ExpensesTable.id eq id }) {
                    it[description] = updatedExpense.description
                    it[amount] = updatedExpense.amount
                    it[category] = updatedExpense.category
                    it[date] = updatedExpense.date
                    it[userId] = updatedExpense.userId
                }
            }
            if (rowsUpdated > 0) {
                call.respond(HttpStatusCode.OK, updatedExpense.copy(id = id))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // DELETE
        delete("/expenses/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest)
                return@delete
            }
            val rowsDeleted = transaction {
                ExpensesTable.deleteWhere { ExpensesTable.id eq id }
            }
            if (rowsDeleted > 0) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }
}