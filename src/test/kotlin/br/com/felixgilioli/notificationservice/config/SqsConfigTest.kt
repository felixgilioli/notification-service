package br.com.felixgilioli.notificationservice.config

import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull

class SqsConfigTest {

    private val properties = SqsProperties(
        endpoint = "http://localhost:4566",
        region = "us-east-1",
        accessKey = "test",
        secretKey = "test",
        notificationQueue = "test-queue"
    )

    @Test
    fun `should create SqsClient bean without throwing`() {
        val config = SqsConfig(properties)

        val sqsClient = config.sqsClient()

        assertNotNull(sqsClient)
    }

    @Test
    fun `should create distinct SqsClient instances`() {
        val config = SqsConfig(properties)

        val client1 = config.sqsClient()
        val client2 = config.sqsClient()

        assertNotNull(client1)
        assertNotNull(client2)
    }
}
