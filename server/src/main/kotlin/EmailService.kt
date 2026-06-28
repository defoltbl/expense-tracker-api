package com.andrii

import com.sendgrid.Method
import com.sendgrid.Request
import com.sendgrid.SendGrid
import com.sendgrid.helpers.mail.Mail
import com.sendgrid.helpers.mail.objects.Content
import com.sendgrid.helpers.mail.objects.Email

object EmailService {

    private val apiKey = System.getenv("SENDGRID_API_KEY")
    private val FROM_EMAIL = System.getenv("SENDGRID_FROM_EMAIL") ?: "t67111658@gmail.com"

    fun sendPasswordResetEmail(toEmail: String, resetToken: String) {
        if (apiKey == null) {
            println("WARNING: SENDGRID_API_KEY not set, skipping email send. Reset token: $resetToken")
            return
        }

        val from = Email(FROM_EMAIL)
        val to = Email(toEmail)
        val subject = "Reset your Expense Tracker password"
        val content = Content(
            "text/plain",
            "You requested a password reset.\n\n" +
                    "Your reset token is: $resetToken\n\n" +
                    "Use this token with the /reset-password endpoint within 1 hour.\n" +
                    "If you didn't request this, you can safely ignore this email."
        )
        val mail = Mail(from, subject, to, content)

        val sg = SendGrid(apiKey)
        val request = Request()
        try {
            request.method = Method.POST
            request.endpoint = "mail/send"
            request.body = mail.build()
            val response = sg.api(request)
            println("SendGrid response: ${response.statusCode}")
        } catch (ex: Exception) {
            println("Failed to send email: ${ex.message}")
        }
    }
}