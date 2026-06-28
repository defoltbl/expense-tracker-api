package com.andrii

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ServerTest {

    private fun uniqueUsername(): String = "testuser_${System.nanoTime()}"

    private fun registerBody(username: String, password: String = "testpass123") =
        """{"username": "$username", "email": "$username@example.com", "password": "$password"}"""

    private fun extractField(json: String, field: String): String? =
        Regex(""""$field"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)

    @Test
    fun `register with valid credentials returns 201`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRateLimiting()
            configureRouting()
        }

        val username = uniqueUsername()
        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(registerBody(username))
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `register with duplicate username returns 409`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRateLimiting()
            configureRouting()
        }

        val username = uniqueUsername()
        val body = registerBody(username)

        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `login with valid credentials returns access and refresh tokens`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRateLimiting()
            configureRouting()
        }

        val username = uniqueUsername()
        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(registerBody(username))
        }

        val response = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$username", "password": "testpass123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertNotNull(extractField(body, "accessToken"), "Response should contain an accessToken field")
        assertNotNull(extractField(body, "refreshToken"), "Response should contain a refreshToken field")
    }

    @Test
    fun `login with invalid credentials returns 401`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRateLimiting()
            configureRouting()
        }

        val response = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "nonexistent_user_xyz", "password": "wrong"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `accessing expenses without a token returns 401`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRateLimiting()
            configureRouting()
        }

        val response = client.get("/expenses")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `accessing expenses with a valid access token returns 200`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRateLimiting()
            configureRouting()
        }

        val username = uniqueUsername()
        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(registerBody(username))
        }

        val loginResponse = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$username", "password": "testpass123"}""")
        }
        val accessToken = extractField(loginResponse.bodyAsText(), "accessToken")
        assertNotNull(accessToken, "Access token should be present in login response")

        val response = client.get("/expenses") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `refreshing with a valid refresh token returns a new access token`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRateLimiting()
            configureRouting()
        }

        val username = uniqueUsername()
        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(registerBody(username))
        }

        val loginResponse = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$username", "password": "testpass123"}""")
        }
        val refreshToken = extractField(loginResponse.bodyAsText(), "refreshToken")
        assertNotNull(refreshToken, "Refresh token should be present in login response")

        val refreshResponse = client.post("/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken": "$refreshToken"}""")
        }

        assertEquals(HttpStatusCode.OK, refreshResponse.status)
        val newAccessToken = extractField(refreshResponse.bodyAsText(), "accessToken")
        assertNotNull(newAccessToken, "Refresh response should contain a new accessToken")

        // Confirm the new access token actually works
        val protectedResponse = client.get("/expenses") {
            header(HttpHeaders.Authorization, "Bearer $newAccessToken")
        }
        assertEquals(HttpStatusCode.OK, protectedResponse.status)
    }

    @Test
    fun `refreshing with an invalid refresh token returns 401`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRateLimiting()
            configureRouting()
        }

        val response = client.post("/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken": "totally-invalid-token-value"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `exceeding the auth rate limit returns 429`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRateLimiting()
            configureRouting()
        }

        val body = """{"username": "rate_limit_test_user", "password": "wrong"}"""

        // The rate limiter allows 5 requests per window; send 6
        repeat(5) {
            client.post("/login") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

        val sixthResponse = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.TooManyRequests, sixthResponse.status)
    }
}