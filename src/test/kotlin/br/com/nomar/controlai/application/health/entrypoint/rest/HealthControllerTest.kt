package br.com.nomar.controlai.application.health.entrypoint.rest

import kotlin.test.Test
import kotlin.test.assertEquals

class HealthControllerTest {

    private val healthController = HealthController()

    @Test
    fun `should return service health status`() {
        assertEquals(mapOf("status" to "UP"), healthController.health())
    }
}
