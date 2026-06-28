package com.andrii

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.JWTVerifier
import java.util.Date
import java.security.SecureRandom
import java.util.Base64

object JwtConfig {
    private val SECRET = System.getenv("JWT_SECRET") ?: "dev-only-secret-change-in-production"
    private const val ISSUER = "expense-tracker-api"
    private const val ACCESS_TOKEN_VALIDITY_MS = 15L * 60 * 1000 // 15 minutes
    const val REFRESH_TOKEN_VALIDITY_DAYS = 30L

    private val algorithm = Algorithm.HMAC256(SECRET)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .build()

    fun generateAccessToken(user: User): String {
        return JWT.create()
            .withIssuer(ISSUER)
            .withClaim("userId", user.id)
            .withClaim("username", user.username)
            .withExpiresAt(Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY_MS))
            .sign(algorithm)
    }

    fun generateRefreshToken(): String {
        val randomBytes = ByteArray(64)
        SecureRandom().nextBytes(randomBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes)
    }
}