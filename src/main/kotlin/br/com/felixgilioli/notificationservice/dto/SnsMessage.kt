package br.com.felixgilioli.notificationservice.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SnsMessage(
    @JsonProperty("Message") val message: String
)