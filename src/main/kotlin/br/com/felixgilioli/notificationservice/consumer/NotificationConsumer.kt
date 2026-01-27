package br.com.felixgilioli.notificationservice.consumer

import br.com.felixgilioli.notificationservice.config.SqsProperties
import br.com.felixgilioli.notificationservice.dto.SnsMessage
import br.com.felixgilioli.notificationservice.dto.VideoCompletedEvent
import br.com.felixgilioli.notificationservice.service.EmailService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.ObjectMapper

@Component
class NotificationConsumer(
    private val sqsClient: SqsClient,
    private val sqsProperties: SqsProperties,
    private val emailService: EmailService,
    private val objectMapper: ObjectMapper
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    fun poll() {
        val messages = sqsClient.receiveMessage {
            it.queueUrl(getQueueUrl()).maxNumberOfMessages(5).waitTimeSeconds(10)
        }.messages()

        messages.forEach { process(it) }
    }

    private fun process(message: Message) {
        try {
            val snsMessage = objectMapper.readValue(message.body(), SnsMessage::class.java)
            val event = objectMapper.readValue(snsMessage.message, VideoCompletedEvent::class.java)

            logger.info("Notificando usuário: ${event.userId} sobre vídeo: ${event.videoId}")

            emailService.sendVideoProcessedEmail(event.userId, event)

            deleteMessage(message)
            logger.info("Notificação enviada: ${event.videoId}")
        } catch (e: Exception) {
            logger.error("Erro ao processar mensagem", e)
        }
    }

    private fun getQueueUrl(): String =
        sqsClient.getQueueUrl { it.queueName(sqsProperties.notificationQueue) }.queueUrl()

    private fun deleteMessage(message: Message) {
        sqsClient.deleteMessage { it.queueUrl(getQueueUrl()).receiptHandle(message.receiptHandle()) }
    }
}