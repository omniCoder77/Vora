package com.ethyllium.authservice.infrastructure.inbound.controller.dto

sealed interface ApiResponse {
    data class Success<T>(val data: T) : ApiResponse
    data class Error(val error: String) : ApiResponse
}