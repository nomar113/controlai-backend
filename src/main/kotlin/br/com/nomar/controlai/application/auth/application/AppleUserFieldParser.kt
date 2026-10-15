package br.com.nomar.controlai.application.auth.application

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

data class AppleUserInfo(
    val fullName: String?,
    val email: String?,
)

// Apple sends the `user` field only on the first authorization, as JSON such as
// {"name":{"firstName":"John","lastName":"Doe"},"email":"john@example.com"}.
// A missing or malformed field must not block the login, so this never throws.
@Component
class AppleUserFieldParser(
    private val objectMapper: ObjectMapper,
) {

    fun parse(rawUser: String?): AppleUserInfo? {
        if (rawUser.isNullOrBlank()) return null

        return runCatching {
            val node = objectMapper.readTree(rawUser)
            val firstName = node.path("name").path("firstName").asText(null)?.trim()
            val lastName = node.path("name").path("lastName").asText(null)?.trim()
            val fullName = listOfNotNull(firstName, lastName)
                .filter(String::isNotEmpty)
                .joinToString(" ")
                .takeIf(String::isNotEmpty)
            val email = node.path("email").asText(null)?.trim()?.takeIf(String::isNotEmpty)

            AppleUserInfo(fullName = fullName, email = email)
        }.onFailure {
            logger.warn("Ignoring malformed Apple user field: {}", it::class.simpleName)
        }.getOrNull()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(AppleUserFieldParser::class.java)
    }
}
