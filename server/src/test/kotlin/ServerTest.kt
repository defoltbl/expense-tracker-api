package com.andrii

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ServerTest {

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
            setBody("""{"username": "nonexistent", "password": "wrong"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }
}