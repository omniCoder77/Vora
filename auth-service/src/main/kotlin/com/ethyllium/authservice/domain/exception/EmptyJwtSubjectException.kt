package com.ethyllium.authservice.domain.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.UNAUTHORIZED, reason = "JWT subject is empty or missing")
class EmptyJwtSubjectException : Exception()