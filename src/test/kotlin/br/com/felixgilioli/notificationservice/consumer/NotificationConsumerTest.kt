package br.com.felixgilioli.notificationservice.consumer

import br.com.felixgilioli.notificationservice.config.SqsProperties
import br.com.felixgilioli.notificationservice.dto.SnsMessage
import br.com.felixgilioli.notificationservice.dto.SnsMessageAttribute
import br.com.felixgilioli.notificationservice.dto.VideoCompletedEvent
import br.com.felixgilioli.notificationservice.service.EmailService
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Scope
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.*
import org.mockito.quality.Strictness
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import tools.jackson.databind.ObjectMapper
import java.util.*
import java.util.function.Consumer

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationConsumerTest {

    @Mock private lateinit var sqsClient: SqsClient
    @Mock private lateinit var emailService: EmailService
    @Mock private lateinit var objectMapper: ObjectMapper
    @Mock private lateinit var tracer: Tracer
    @Mock private lateinit var spanBuilder: SpanBuilder
    @Mock private lateinit var span: Span
    @Mock private lateinit var scope: Scope

    private val sqsProperties = SqsProperties(
        endpoint = "http://localhost:4566",
        region = "us-east-1",
        accessKey = "test",
        secretKey = "test",
        notificationQueue = "test-queue"
    )

    private lateinit var consumer: NotificationConsumer

    private val videoId = UUID.randomUUID()
    private val userId = "user@example.com"

    private val event = VideoCompletedEvent(
        videoId = videoId,
        userId = userId,
        status = "READY",
        zipUrl = "http://example.com/frames.zip"
    )
    private val snsMessage = SnsMessage(
        message = """{"videoId":"$videoId","userId":"$userId","status":"READY","zipUrl":"http://example.com/frames.zip"}"""
    )

    @BeforeEach
    fun setUp() {
        consumer = NotificationConsumer(sqsClient, sqsProperties, emailService, objectMapper, tracer)

        whenever(tracer.spanBuilder("send-notification")).thenReturn(spanBuilder)
        whenever(spanBuilder.setParent(any())).thenReturn(spanBuilder)
        whenever(spanBuilder.startSpan()).thenReturn(span)
        whenever(span.makeCurrent()).thenReturn(scope)
    }

    @Test
    fun `poll should not interact with email service when no messages`() {
        mockReceiveMessages()

        consumer.poll()

        verifyNoInteractions(emailService, tracer)
    }

    @Test
    fun `poll should process each received message`() {
        mockReceiveMessages(buildSqsMessage("handle-1"), buildSqsMessage("handle-2"))

        doReturn(snsMessage).whenever(objectMapper).readValue(any<String>(), eq(SnsMessage::class.java))
        doReturn(event).whenever(objectMapper).readValue(any<String>(), eq(VideoCompletedEvent::class.java))

        consumer.poll()

        verify(emailService, times(2)).sendVideoProcessedEmail(eq(userId), eq(event))
    }

    @Test
    fun `process should send email and delete message on success`() {
        mockReceiveMessages(buildSqsMessage())

        doReturn(snsMessage).whenever(objectMapper).readValue(any<String>(), eq(SnsMessage::class.java))
        doReturn(event).whenever(objectMapper).readValue(any<String>(), eq(VideoCompletedEvent::class.java))

        consumer.poll()

        verify(emailService).sendVideoProcessedEmail(userId, event)
        verify(sqsClient).deleteMessage(any<Consumer<DeleteMessageRequest.Builder>>())
        verify(span).end()
    }

    @Test
    fun `process should return early without starting span when SNS body is invalid JSON`() {
        mockReceiveMessages(buildSqsMessage())

        doThrow(RuntimeException("Parse error"))
            .whenever(objectMapper).readValue(any<String>(), eq(SnsMessage::class.java))

        consumer.poll()

        verifyNoInteractions(tracer, emailService)
        verify(sqsClient, never()).deleteMessage(any<Consumer<DeleteMessageRequest.Builder>>())
    }

    @Test
    fun `process should record exception on span and not delete message when email fails`() {
        mockReceiveMessages(buildSqsMessage())

        doReturn(snsMessage).whenever(objectMapper).readValue(any<String>(), eq(SnsMessage::class.java))
        doReturn(event).whenever(objectMapper).readValue(any<String>(), eq(VideoCompletedEvent::class.java))

        val exception = RuntimeException("SMTP error")
        whenever(emailService.sendVideoProcessedEmail(any(), any())).thenThrow(exception)

        consumer.poll()

        verify(span).recordException(exception)
        verify(span).setStatus(StatusCode.ERROR, "SMTP error")
        verify(sqsClient, never()).deleteMessage(any<Consumer<DeleteMessageRequest.Builder>>())
        verify(span).end()
    }

    @Test
    fun `process should propagate trace context when traceparent attribute is present`() {
        val traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"
        val snsMessageWithTrace = snsMessage.copy(
            messageAttributes = mapOf("traceparent" to SnsMessageAttribute(type = "String", value = traceparent))
        )

        mockReceiveMessages(buildSqsMessage())

        doReturn(snsMessageWithTrace).whenever(objectMapper).readValue(any<String>(), eq(SnsMessage::class.java))
        doReturn(event).whenever(objectMapper).readValue(any<String>(), eq(VideoCompletedEvent::class.java))

        consumer.poll()

        verify(spanBuilder).setParent(any())
        verify(emailService).sendVideoProcessedEmail(userId, event)
    }

    @Test
    fun `process should set span attributes with videoId and userId`() {
        mockReceiveMessages(buildSqsMessage())

        doReturn(snsMessage).whenever(objectMapper).readValue(any<String>(), eq(SnsMessage::class.java))
        doReturn(event).whenever(objectMapper).readValue(any<String>(), eq(VideoCompletedEvent::class.java))

        consumer.poll()

        verify(span).setAttribute("videoId", videoId.toString())
        verify(span).setAttribute("userId", userId)
    }

    private fun mockReceiveMessages(vararg messages: Message) {
        val response = ReceiveMessageResponse.builder().messages(messages.toList()).build()
        whenever(sqsClient.receiveMessage(any<Consumer<ReceiveMessageRequest.Builder>>())).thenReturn(response)
    }

    private fun buildSqsMessage(receiptHandle: String = "receipt-handle"): Message =
        Message.builder()
            .body("""{"Message":"{}"}""")
            .receiptHandle(receiptHandle)
            .build()
}
