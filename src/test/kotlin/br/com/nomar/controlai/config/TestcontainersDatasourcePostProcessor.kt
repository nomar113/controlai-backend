package br.com.nomar.controlai.config

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

// Points every test run at a disposable MySQL started by Testcontainers (one container per test
// JVM, shared by every cached Spring context). Registered in src/test/resources/META-INF/
// spring.factories, so it applies to any runner (Gradle or IDE). It adds the highest-precedence
// property source, because ./application.yml and a DB_URL env var both outrank
// src/test/resources/application.properties — the suite never touches the local
// docker-compose database nor any database DB_URL points to.
class TestcontainersDatasourcePostProcessor : EnvironmentPostProcessor, Ordered {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        environment.propertySources.addFirst(
            MapPropertySource(
                "testcontainers-datasource",
                mapOf(
                    "spring.datasource.url" to DATASOURCE_URL,
                    "spring.datasource.driver-class-name" to "org.testcontainers.jdbc.ContainerDatabaseDriver",
                ),
            ),
        )
    }

    // Run after the config data (application.yml/properties) has been loaded.
    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    companion object {
        const val DATASOURCE_URL =
            "jdbc:tc:mysql:8.0:///controlai?TC_DAEMON=true&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
    }
}
