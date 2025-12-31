package com.ethyllium.authservice.infrastructure.inbound.controller

import com.ethyllium.authservice.domain.port.driver.LoginService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/auth/mfa")
class MfaSetupController(private val loginService: LoginService) {

    @PostMapping("/setup/initiate")
    fun initiateSetup(authentication: Authentication): Mono<ResponseEntity<Map<String, String>>> {
        val userId = authentication.name
        return loginService.initiateMfaSetup(userId)
            .map { details ->
                ResponseEntity.ok(
                    mapOf(
                        "secret" to details.secret,
                        "qrCodeUri" to details.qrCodeUri
                    )
                )
            }
    }

    data class MfaConfirmRequest(val code: Int)

    @PostMapping("/setup/confirm")
    fun confirmSetup(
        authentication: Authentication,
        @RequestBody request: MfaConfirmRequest
    ): Mono<ResponseEntity<Any>> {
        val userId = authentication.name
        return loginService.confirmMfaSetup(userId, request.code)
            .map { success ->
                if (success) {
                    ResponseEntity.ok().body(mapOf("message" to "MFA Enabled successfully"))
                } else {
                    ResponseEntity.badRequest().body(mapOf("message" to "Invalid Code"))
                }
            }
    }
}