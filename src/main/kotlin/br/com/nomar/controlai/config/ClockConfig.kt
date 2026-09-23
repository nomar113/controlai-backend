package br.com.nomar.controlai.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class ClockConfig {

    // Injected wherever "now" drives a business rule, so tests can pin it with a fixed clock
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
