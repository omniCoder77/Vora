package com.ethyllium.authservice.domain.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.CONFLICT, reason = "User already exists")
class UserAlreadyExistsException : RuntimeException()

