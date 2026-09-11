package br.com.nomar.controlai.config

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Exercises CorsConfig/SecurityConfig end-to-end via a preflight OPTIONS request against the
// public /health route, proving the ControlAI Web origin is accepted and an unlisted origin is
// still blocked, without touching CorsConfig.kt/SecurityConfig.kt (config-only change).
@SpringBootTest(
    properties = [
        "app.cors.allowed-origin-patterns=" +
            "https://controlai.opencod3.com.br," +
            "https://controlai-web-sepia.vercel.app," +
            "https://controlai-*-ramon-mesquitas-projects.vercel.app",
    ],
)
@AutoConfigureMockMvc
class CorsConfigIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `preflight from the ControlAI Web origin is accepted`() {
        mockMvc.perform(
            options("/health")
                .header(HttpHeaders.ORIGIN, "https://controlai.opencod3.com.br")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://controlai.opencod3.com.br"))
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
    }

    @Test
    fun `preflight from the Vercel production origin is accepted`() {
        mockMvc.perform(
            options("/health")
                .header(HttpHeaders.ORIGIN, "https://controlai-web-sepia.vercel.app")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://controlai-web-sepia.vercel.app"))
    }

    @Test
    fun `preflight from a Vercel preview origin matching the wildcard pattern is accepted`() {
        val previewOrigin = "https://controlai-abc123-ramon-mesquitas-projects.vercel.app"

        mockMvc.perform(
            options("/health")
                .header(HttpHeaders.ORIGIN, previewOrigin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"),
        )
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, previewOrigin))
    }

    @Test
    fun `preflight from an origin not in the allowlist is rejected`() {
        mockMvc.perform(
            options("/health")
                .header(HttpHeaders.ORIGIN, "https://attacker.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"),
        )
            .andExpect(status().isForbidden)
            .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
    }
}
