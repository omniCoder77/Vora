package com.ethyllium.authservice.infrastructure.inbound.controller

import com.ethyllium.authservice.application.service.RegisterResult
import com.ethyllium.authservice.domain.port.driver.RegisterService
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.ApiResponse
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.JwtTokenResponse
import com.ethyllium.authservice.infrastructure.inbound.controller.dto.RegisterRequest
import io.reactivex.rxjava3.internal.util.QueueDrainHelper.request
import org.redisson.remote.ResponseEntry
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/v1/register")
class RegisterController(private val registerService: RegisterService) {

	@PostMapping
	fun register(@RequestBody request: RegisterRequest): Mono<ApiResponse> {
		return registerService.register(password = request.password,email = request.email,mfaOptions = request.mfaOptions)
		.flatMap { 
			when(it) {
                is RegisterResult.Authenticator -> {
					Mono.just(ApiResponse.Success(ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(it.byteArray)))
				}
                RegisterResult.EmailVerification -> {
					Mono.just(ApiResponse.Success(ResponseEntity.ok().body("Email verification sent")))
				}
                is RegisterResult.Token -> {
					Mono.just(ApiResponse.Success(ResponseEntity.ok().body(it.token)))
				}
            }
		}
	}
}
