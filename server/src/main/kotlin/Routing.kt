package com.andrii

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun ApplicationCall.requireUserId(): Int {
    val principal = principal<JWTPrincipal>()!!
    return principal.payload.getClaim("userId").asInt()
}

fun Application.configureAuthentication() {
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(JwtConfig.verifier)
            validate { credential ->
                if (credential.payload.getClaim("userId").asInt() != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }
}

fun Application.configureRouting() {
    routing {
        // Public route — no token required
        post("/login") {
            val loginRequest = call.receive<LoginRequest>()
            val user = UserRepository.findByUsername(loginRequest.username)

            if (user == null || !UserRepository.verifyPassword(loginRequest.password, user.passwordHash)) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid username or password")
                return@post
            }

            val token = JwtConfig.generateToken(user)
            call.respond(LoginResponse(token))
        }

        post("/register") {
            val registerRequest = call.receive<RegisterRequest>()

            if (registerRequest.username.isBlank() || registerRequest.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Username and password must not be empty")
                return@post
            }

            val newUser = UserRepository.createUser(registerRequest.username, registerRequest.password)

            if (newUser == null) {
                call.respond(HttpStatusCode.Conflict, "Username already exists")
                return@post
            }

            call.respond(HttpStatusCode.Created, "User registered successfully")
        }

        // Protected routes — require a valid JWT
        authenticate("auth-jwt") {

            post("/expenses") {
                val tokenUserId = call.requireUserId()

                val newExpense = call.receive<Expense>()
                val insertedId = transaction {
                    ExpensesTable.insert {
                        it[description] = newExpense.description
                        it[amount] = newExpense.amount
                        it[category] = newExpense.category
                        it[date] = newExpense.date
                        it[userId] = tokenUserId
                    } get ExpensesTable.id
                }
                val created = newExpense.copy(id = insertedId, userId = tokenUserId)
                call.respond(HttpStatusCode.Created, created)
            }

            get("/expenses") {
                val tokenUserId = call.requireUserId()

                val userExpenses = transaction {
                    ExpensesTable.selectAll().where { ExpensesTable.userId eq tokenUserId }.map {
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
                call.respond(userExpenses)
            }

            get("/expenses/{id}") {
                val tokenUserId = call.requireUserId()

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@get
                }
                val expense = transaction {
                    ExpensesTable.selectAll()
                        .where { (ExpensesTable.id eq id) and (ExpensesTable.userId eq tokenUserId) }
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

            put("/expenses/{id}") {
                val tokenUserId = call.requireUserId()

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@put
                }
                val updatedExpense = call.receive<Expense>()
                val rowsUpdated = transaction {
                    ExpensesTable.update({ (ExpensesTable.id eq id) and (ExpensesTable.userId eq tokenUserId) }) {
                        it[description] = updatedExpense.description
                        it[amount] = updatedExpense.amount
                        it[category] = updatedExpense.category
                        it[date] = updatedExpense.date
                        it[userId] = tokenUserId
                    }
                }
                if (rowsUpdated > 0) {
                    call.respond(HttpStatusCode.OK, updatedExpense.copy(id = id, userId = tokenUserId))
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            delete("/expenses/{id}") {
                val tokenUserId = call.requireUserId()

                val id = call.parameters["id"]?.toIntOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@delete
                }
                val rowsDeleted = transaction {
                    ExpensesTable.deleteWhere { (ExpensesTable.id eq id) and (ExpensesTable.userId eq tokenUserId) }
                }
                if (rowsDeleted > 0) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }
        }
    }
}