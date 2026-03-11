package br.com.felixgilioli.notificationservice.service

import br.com.felixgilioli.notificationservice.dto.VideoCompletedEvent
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mail.javamail.JavaMailSender
import java.util.*
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
class EmailServiceTest {

    @Mock
    private lateinit var mailSender: JavaMailSender

    private lateinit var emailService: EmailService
    private lateinit var mimeMessage: MimeMessage

    @BeforeEach
    fun setUp() {
        mimeMessage = MimeMessage(Session.getDefaultInstance(Properties()))
        whenever(mailSender.createMimeMessage()).thenReturn(mimeMessage)
        emailService = EmailService(mailSender)
    }

    @Test
    fun `should send email with success subject when status is READY`() {
        val event = VideoCompletedEvent(
            videoId = UUID.randomUUID(),
            userId = "user@example.com",
            status = "READY",
            zipUrl = "http://example.com/frames.zip"
        )

        emailService.sendVideoProcessedEmail("user@example.com", event)

        verify(mailSender).send(mimeMessage)
        assertEquals("Seu vídeo foi processado com sucesso!", mimeMessage.subject)
    }

    @Test
    fun `should send email with error subject when status is not READY`() {
        val event = VideoCompletedEvent(
            videoId = UUID.randomUUID(),
            userId = "user@example.com",
            status = "ERROR",
            zipUrl = null
        )

        emailService.sendVideoProcessedEmail("user@example.com", event)

        verify(mailSender).send(mimeMessage)
        assertEquals("Erro ao processar seu vídeo", mimeMessage.subject)
    }

    @Test
    fun `should not throw when mail sender throws exception`() {
        val event = VideoCompletedEvent(
            videoId = UUID.randomUUID(),
            userId = "user@example.com",
            status = "READY",
            zipUrl = "http://example.com/frames.zip"
        )
        whenever(mailSender.send(any<MimeMessage>())).thenThrow(RuntimeException("SMTP error"))

        emailService.sendVideoProcessedEmail("user@example.com", event)
    }

    @Test
    fun `email body should contain videoId and zip url when status is READY`() {
        val videoId = UUID.randomUUID()
        val zipUrl = "http://example.com/frames.zip"
        val event = VideoCompletedEvent(
            videoId = videoId,
            userId = "user@example.com",
            status = "READY",
            zipUrl = zipUrl
        )

        emailService.sendVideoProcessedEmail("user@example.com", event)

        val content = extractBodyContent()
        assert(content.contains(videoId.toString())) { "Body should contain videoId" }
        assert(content.contains(zipUrl)) { "Body should contain zip URL" }
    }

    @Test
    fun `email body should contain videoId when status is not READY`() {
        val videoId = UUID.randomUUID()
        val event = VideoCompletedEvent(
            videoId = videoId,
            userId = "user@example.com",
            status = "FAILED",
            zipUrl = null
        )

        emailService.sendVideoProcessedEmail("user@example.com", event)

        val content = extractBodyContent()
        assert(content.contains(videoId.toString())) { "Body should contain videoId" }
    }

    @Test
    fun `should send to the correct recipient`() {
        val recipient = "recipient@example.com"
        val event = VideoCompletedEvent(
            videoId = UUID.randomUUID(),
            userId = recipient,
            status = "READY",
            zipUrl = "http://example.com/frames.zip"
        )

        emailService.sendVideoProcessedEmail(recipient, event)

        assertEquals(recipient, mimeMessage.allRecipients.first().toString())
    }

    private fun extractBodyContent(): String {
        val multipart = mimeMessage.content as jakarta.mail.internet.MimeMultipart
        val alternative = (multipart.getBodyPart(0) as jakarta.mail.internet.MimeBodyPart)
            .content as jakarta.mail.internet.MimeMultipart
        return alternative.getBodyPart(0).content.toString()
    }
}
