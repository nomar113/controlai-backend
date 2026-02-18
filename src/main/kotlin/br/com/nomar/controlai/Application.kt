package br.com.nomar.controlai

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
@EnableJpaAuditing
class ControlaiApplication

fun main(args: Array<String>) {
	runApplication<ControlaiApplication>(*args)
}
