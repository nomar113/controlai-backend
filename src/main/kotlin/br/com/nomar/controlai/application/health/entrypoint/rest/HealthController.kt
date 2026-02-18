package br.com.nomar.controlai.application.health.entrypoint.rest

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/health")
class HealthController {

    @GetMapping
    fun health(): Map<String, String> = mapOf("status" to "UP")
}
