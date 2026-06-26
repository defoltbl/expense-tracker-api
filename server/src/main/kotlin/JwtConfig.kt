package com.andrii

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.JWTVerifier
import java.util.Date

object JwtConfig {
    private const val SECRET = "your-256-bit-secret-change-this-later"
    private const val ISSUER = "expense-tracker-api"
    private const val VALIDITY_IN_MS = 36_000_00 * 24 // 24 hours

    private val algorithm = Algorithm.HMAC256(SECRET)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .build()

    fun generateToken(user: User): String {
        return JWT.create()
            .withIssuer(ISSUER)
            .withClaim("userId", user.id)
            .withClaim("username", user.username)
            .withExpiresAt(Date(System.currentTimeMillis() + VALIDITY_IN_MS))
            .sign(algorithm)
    }
}