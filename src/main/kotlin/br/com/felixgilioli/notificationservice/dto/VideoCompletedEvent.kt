package br.com.felixgilioli.notificationservice.dto

import java.util.UUID

data class VideoCompletedEvent(
    val videoId: UUID,
    val userId: String,
    val status: String,
    val zipUrl: String?
)