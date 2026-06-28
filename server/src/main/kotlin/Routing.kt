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
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.seconds
import java.time.LocalDateTime

fun ApplicationCall.requireUserId(): Int {
    val principal = principal<JWTPrincipal>()!!
    return principal.payload.getClaim("userId").asInt()
}

fun Application.configureRateLimiting() {
    install(RateLimit) {
        register(RateLimitName("auth")) {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
        }
    }
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

        get("/") {
            call.respond(
                mapOf(
                    "status" to "ok",
                    "service" to "Expense Tracker API",
                    "docs" to "https://github.com/defoltbl/expense-tracker-api"
                )
            )
        }

        rateLimit(RateLimitName("auth")) {
            // Public route — no token required
            post("/login") {
                val loginRequest = call.receive<LoginRequest>()
                val user = UserRepository.findByUsername(loginRequest.username)

                if (user == null || !UserRepository.verifyPassword(loginRequest.password, user.passwordHash)) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid username or password")
                    return@post
                }

                val accessToken = JwtConfig.generateAccessToken(user)
                val refreshToken = JwtConfig.generateRefreshToken()
                val refreshTokenExpiresAt = LocalDateTime.now().plusDays(JwtConfig.REFRESH_TOKEN_VALIDITY_DAYS)

                transaction {
                    RefreshTokensTable.insert {
                        it[userId] = user.id
                        it[token] = refreshToken
                        it[expiresAt] = refreshTokenExpiresAt
                    }
                }

                call.respond(TokenResponse(accessToken, refreshToken))
            }

            post("/refresh") {
                val refreshRequest = call.receive<RefreshRequest>()

                val storedToken = transaction {
                    RefreshTokensTable.selectAll()
                        .where { RefreshTokensTable.token eq refreshRequest.refreshToken }
                        .singleOrNull()
                }

                if (storedToken == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid refresh token")
                    return@post
                }

                val expiresAt = storedToken[RefreshTokensTable.expiresAt]
                if (expiresAt.isBefore(LocalDateTime.now())) {
                    transaction {
                        RefreshTokensTable.deleteWhere { RefreshTokensTable.token eq refreshRequest.refreshToken }
                    }
                    call.respond(HttpStatusCode.Unauthorized, "Refresh token has expired")
                    return@post
                }

                val userId = storedToken[RefreshTokensTable.userId]
                val user = transaction {
                    UsersTable.selectAll().where { UsersTable.id eq userId }
                        .map {
                            User(
                                id = it[UsersTable.id],
                                username = it[UsersTable.username],
                                email = it[UsersTable.email],
                                passwordHash = it[UsersTable.passwordHash]
                            )
                        }.singleOrNull()
                }

                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, "User no longer exists")
                    return@post
                }

                val newAccessToken = JwtConfig.generateAccessToken(user)
                call.respond(mapOf("accessToken" to newAccessToken))
            }

            post("/register") {
                val registerRequest = call.receive<RegisterRequest>()

                if (registerRequest.username.isBlank() || registerRequest.password.isBlank() || registerRequest.email.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, "Username, email, and password must not be empty")
                    return@post
                }

                val newUser = UserRepository.createUser(registerRequest.username, registerRequest.email, registerRequest.password)

                if (newUser == null) {
                    call.respond(HttpStatusCode.Conflict, "Username already exists")
                    return@post
                }

                call.respond(HttpStatusCode.Created, "User registered successfully")
            }

            post("/forgot-password") {
                val forgotRequest = call.receive<ForgotPasswordRequest>()
                val user = UserRepository.findByUsername(forgotRequest.username)

                // Always respond the same way, regardless of whether the user exists —
                // prevents leaking which usernames are registered
                if (user == null) {
                    call.respond(HttpStatusCode.OK, "If that account exists, a reset email has been sent")
                    return@post
                }

                val resetToken = JwtConfig.generateRefreshToken() // reuse the same random-token generator
                val expiresAt = LocalDateTime.now().plusHours(1)

                transaction {
                    PasswordResetTokensTable.insert {
                        it[userId] = user.id
                        it[token] = resetToken
                        it[PasswordResetTokensTable.expiresAt] = expiresAt
                    }
                }

                EmailService.sendPasswordResetEmail(user.email, resetToken)

                call.respond(HttpStatusCode.OK, "If that account exists, a reset email has been sent")
            }

            post("/reset-password") {
                val resetRequest = call.receive<ResetPasswordRequest>()

                val storedToken = transaction {
                    PasswordResetTokensTable.selectAll()
                        .where { PasswordResetTokensTable.token eq resetRequest.resetToken }
                        .singleOrNull()
                }

                if (storedToken == null) {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid or expired reset token")
                    return@post
                }

                val tokenExpiresAt = storedToken[PasswordResetTokensTable.expiresAt]
                if (tokenExpiresAt.isBefore(LocalDateTime.now())) {
                    transaction {
                        PasswordResetTokensTable.deleteWhere { PasswordResetTokensTable.token eq resetRequest.resetToken }
                    }
                    call.respond(HttpStatusCode.Unauthorized, "Invalid or expired reset token")
                    return@post
                }

                val targetUserId = storedToken[PasswordResetTokensTable.userId]
                val newHashedPassword = UserRepository.hashPassword(resetRequest.newPassword)

                transaction {
                    UsersTable.update({ UsersTable.id eq targetUserId }) {
                        it[passwordHash] = newHashedPassword
                    }
                    // Invalidate the reset token so it can't be reused
                    PasswordResetTokensTable.deleteWhere { PasswordResetTokensTable.token eq resetRequest.resetToken }
                }

                call.respond(HttpStatusCode.OK, "Password reset successfully")
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
}
