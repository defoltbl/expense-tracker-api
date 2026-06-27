package com.andrii

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ServerTest {

    private fun uniqueUsername(): String = "testuser_${System.nanoTime()}"

    @Test
    fun `register with valid credentials returns 201`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRouting()
        }

        val username = uniqueUsername()
        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$username", "password": "testpass123"}""")
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Test
    fun `register with duplicate username returns 409`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRouting()
        }

        val username = uniqueUsername()
        val body = """{"username": "$username", "password": "testpass123"}"""

        // First registration should succeed
        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        // Second registration with same username should fail
        val response = client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `login with valid credentials returns a token`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRouting()
        }

        val username = uniqueUsername()
        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$username", "password": "testpass123"}""")
        }

        val response = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$username", "password": "testpass123"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("token"), "Response should contain a token field")
    }

    @Test
    fun `login with invalid credentials returns 401`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
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
            configureRouting()
        }

        val response = client.get("/expenses")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `accessing expenses with a valid token returns 200`() = testApplication {
        application {
            DatabaseFactory.init()
            configureSerialization()
            configureAuthentication()
            configureRouting()
        }

        val username = uniqueUsername()
        client.post("/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$username", "password": "testpass123"}""")
        }

        val loginResponse = client.post("/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"username": "$username", "password": "testpass123"}""")
        }
        val loginBody = loginResponse.bodyAsText()
        val token = Regex(""""token"\s*:\s*"([^"]+)"""").find(loginBody)?.groupValues?.get(1)
        assertNotNull(token, "Token should be present in login response")

        val response = client.get("/expenses") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }
}