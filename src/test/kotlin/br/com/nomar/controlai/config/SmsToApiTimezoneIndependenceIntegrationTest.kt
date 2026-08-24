package br.com.nomar.controlai.config

import br.com.nomar.controlai.application.payments_notification.application.PaymentNotificationTextParser
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.TimeZone

/**
 * Tarefa 7.3: fecha o pipeline parser -> persistencia -> serializacao, parametrizado pelo
 * timezone default da JVM, para provar que o horario de Brasilia devolvido pela API nao depende
 * de onde o backend roda. Chama PaymentNotificationTextParser e o repository diretamente (o
 * wiring da fila SQS em si e coberto por PaymentNotificationQueueListenerTest, nao tocado aqui).
 * Complementa PaymentNotificationTextParserTest (parser isolado) e
 * EntityTimezoneIndependenceIntegrationTest (persistencia isolada), unindo as duas pontas.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SmsToApiTimezoneIndependenceIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var paymentNotificationRepository: PaymentNotificationRepository

    private val parser = PaymentNotificationTextParser()

    @AfterEach
    fun tearDown() {
        paymentNotificationRepository.deleteAll(
            paymentNotificationRepository.findAll().filter { it.merchantName == "LOJA NOTURNA TIMEZONE" }
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["UTC", "America/Sao_Paulo", "Asia/Tokyo"])
    fun `SMS near midnight in Sao Paulo returns the same UTC instant from the API regardless of host timezone`(hostZoneId: String) {
        val originalDefault = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(hostZoneId))

            val text = "FINAL 1234 EM 10/01/2026 23:30 NO VALOR DE R$ 50,00 LOJA NOTURNA TIMEZONE."
            val saved = paymentNotificationRepository.save(parser.parse(text, "NUBANK", "SMS"))

            mockMvc.perform(get("/payments/notifications/${saved.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.purchasedAt").value("2026-01-11T02:30:00Z"))
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }
}
