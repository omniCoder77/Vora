package com.ethyllium.authservice.infrastructure.outbound.security

import com.ethyllium.authservice.domain.port.driven.JwtTokenService
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class JwtAuthenticationFilter(private val jwtTokenService: JwtTokenService,
                              private val userDetailsService: AuthUserDetailService
) : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        val authHeader = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange)
        }

        val token = authHeader.removePrefix("Bearer ")

        return Mono.justOrEmpty(jwtTokenService.getSubject(token)).flatMap { username ->
            userDetailsService.findByUsername(username).flatMap { userDetails ->
                val authentication = UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.authorities
                )
                chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            }
        }
            .switchIfEmpty(chain.filter(exchange))
    }
}