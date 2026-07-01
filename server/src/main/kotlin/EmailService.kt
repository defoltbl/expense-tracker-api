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

    fun sendVerificationEmail(toEmail: String, verificationToken: String) {
        val apiKey = System.getenv("SENDGRID_API_KEY")
        if (apiKey.isNullOrBlank()) {
            println("WARNING: SENDGRID_API_KEY not set, skipping email send. Verification token: $verificationToken")
            return
        }

        val fromEmail = System.getenv("SENDGRID_FROM_EMAIL") ?: "t67111658@gmail.com"

        val from = Email(fromEmail)
        val subject = "Verify your Expense Tracker email"
        val to = Email(toEmail)
        val bodyText = "Welcome! Please verify your email address. Your verification token is: " +
                "$verificationToken Use this token with the /verify-email endpoint within 24 hours. " +
                "If you didn't create this account, you can safely ignore this email."
        val content = Content("text/plain", bodyText)
        val mail = Mail(from, subject, to, content)

        val sg = SendGrid(apiKey)
        val request = Request()
        request.method = Method.POST
        request.endpoint = "mail/send"
        request.body = mail.build()

        val response = sg.api(request)
        println("SendGrid response: ${response.statusCode}")
    }
}