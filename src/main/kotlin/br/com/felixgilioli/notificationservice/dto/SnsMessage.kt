package br.com.felixgilioli.notificationservice.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SnsMessage(
    @JsonProperty("Message") val message: String,
    @JsonProperty("MessageAttributes") val messageAttributes: Map<String, SnsMessageAttribute>? = null
)

data class SnsMessageAttribute(
    @JsonProperty("Type") val type: String,
    @JsonProperty("Value") val value: String
)