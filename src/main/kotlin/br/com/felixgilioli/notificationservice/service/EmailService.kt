package br.com.felixgilioli.notificationservice.service

import br.com.felixgilioli.notificationservice.dto.VideoCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class EmailService(private val mailSender: JavaMailSender) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun sendVideoProcessedEmail(to: String, event: VideoCompletedEvent) {
        val subject = if (event.status == "READY")
            "Seu vídeo foi processado com sucesso!"
        else
            "Erro ao processar seu vídeo"

        val body = buildEmailBody(event)

        try {
            val message = mailSender.createMimeMessage()
            MimeMessageHelper(message, true).apply {
                setTo(to)
                setSubject(subject)
                setText(body, true)
            }
            mailSender.send(message)
            logger.info("Email enviado para: $to")
        } catch (e: Exception) {
            logger.error("Erro ao enviar email para: $to", e)
        }
    }

    private fun buildEmailBody(event: VideoCompletedEvent): String {
        return if (event.status == "READY") {
            """
            <h2>Vídeo processado com sucesso!</h2>
            <p>Seu vídeo <strong>${event.videoId}</strong> foi processado.</p>
            <p><a href="${event.zipUrl}">Clique aqui para baixar os frames</a></p>
            """.trimIndent()
        } else {
            """
            <h2>Erro no processamento</h2>
            <p>Ocorreu um erro ao processar seu vídeo <strong>${event.videoId}</strong>.</p>
            <p>Por favor, tente novamente.</p>
            """.trimIndent()
        }
    }
}