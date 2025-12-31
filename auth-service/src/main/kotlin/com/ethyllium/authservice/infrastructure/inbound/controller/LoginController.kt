package com.ethyllium.authservice.infrastructure.inbound.controller

import com.ethyllium.authservice.application.service.LoginResult
import com.ethyllium.authservice.domain.port.driver.LoginService
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.LoginRequest
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.JwtTokenResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1")
class LoginController(private val loginService: LoginService) {

    @PostMapping("/login")
    fun login(@RequestBody loginRequest: LoginRequest): Mono<ResponseEntity<Any>> {
        return loginService.login(loginRequest.email, loginRequest.password)
            .map { result ->
                when (result) {
                    is LoginResult.Success -> ResponseEntity.ok(
                        JwtTokenResponse(accessToken = result.accessToken, expiresIn = 3600)
                    )
                    is LoginResult.RequiresMfa -> ResponseEntity.status(HttpStatus.ACCEPTED).body(
                        mapOf(
                            "message" to "MFA required",
                            "mfaType" to result.type,
                            "tempToken" to result.tempToken
                        )
                    )
                }
            }
    }

    data class MfaLoginRequest(val tempToken: String, val code: String)

    @PostMapping("/login/mfa")
    fun verifyMfa(@RequestBody request: MfaLoginRequest): Mono<ResponseEntity<JwtTokenResponse>> {
        return loginService.verifyMfaAndLogin(request.tempToken, request.code.toInt())
            .map { result ->
                when (result) {
                    is LoginResult.Success -> ResponseEntity.ok(
                        JwtTokenResponse(accessToken = result.accessToken, expiresIn = 3600)
                    )
                    else -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
                }
            }
    }
}