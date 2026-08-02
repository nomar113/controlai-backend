package br.com.nomar.controlai.application.auth.entrypoint.rest

import br.com.nomar.controlai.application.auth.entrypoint.rest.request.ChangePasswordRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.response.ApiKeyCreatedResponse
import br.com.nomar.controlai.application.auth.entrypoint.rest.response.ApiKeyResponse
import br.com.nomar.controlai.application.auth.entrypoint.rest.response.ProfileResponse
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.auth.exception.InvalidCredentialsException
import br.com.nomar.controlai.domain.auth.usecase.ChangePasswordUseCase
import br.com.nomar.controlai.domain.auth.usecase.CreateApiKeyUseCase
import br.com.nomar.controlai.domain.auth.usecase.GetApiKeyUseCase
import br.com.nomar.controlai.domain.auth.usecase.GetProfileUseCase
import br.com.nomar.controlai.domain.auth.usecase.RevokeApiKeyUseCase
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class ProfileController(
    private val getProfileUseCase: GetProfileUseCase,
    private val createApiKeyUseCase: CreateApiKeyUseCase,
    private val getApiKeyUseCase: GetApiKeyUseCase,
    private val revokeApiKeyUseCase: RevokeApiKeyUseCase,
    private val changePasswordUseCase: ChangePasswordUseCase,
    private val requestContext: RequestContext,
) {

    @GetMapping("/me")
    fun me(): ProfileResponse =
        getProfileUseCase.execute(requestContext.userId)
            .map(ProfileResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }

    @PostMapping("/me/api-key")
    @ResponseStatus(HttpStatus.CREATED)
    fun createApiKey(): ApiKeyCreatedResponse {
        val (apiKey, rawKey) = createApiKeyUseCase.execute(requestContext.groupId).getOrElse { ex ->
            when (ex) {
                is IllegalStateException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
            }
        }
        return ApiKeyCreatedResponse(
            id = apiKey.id!!,
            label = apiKey.label,
            key = rawKey,
            createdAt = apiKey.createdAt,
        )
    }

    @GetMapping("/me/api-key")
    fun getApiKey(): ApiKeyResponse {
        val apiKey = getApiKeyUseCase.execute(requestContext.groupId)
            .getOrElse { throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, it.message) }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No API key found for this group")
        return ApiKeyResponse.from(apiKey)
    }

    @DeleteMapping("/me/api-key")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revokeApiKey() {
        revokeApiKeyUseCase.execute(requestContext.groupId).getOrElse { ex ->
            when (ex) {
                is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                is IllegalStateException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
            }
        }
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun changePassword(@Validated @RequestBody request: ChangePasswordRequest) {
        changePasswordUseCase.execute(requestContext.userId, request.currentPassword, request.newPassword)
            .getOrElse { ex ->
                when (ex) {
                    is InvalidCredentialsException -> throw ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.message)
                    is IllegalArgumentException -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
                    else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
                }
            }
    }
}
