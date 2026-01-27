package br.com.felixgilioli.notificationservice

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class NotificationServiceApplication

fun main(args: Array<String>) {
	runApplication<NotificationServiceApplication>(*args)
}
