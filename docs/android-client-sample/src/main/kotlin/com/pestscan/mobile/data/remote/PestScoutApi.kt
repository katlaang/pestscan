package com.pestscan.mobile.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PestScoutApi {
    @POST("/api/auth/login")
    suspend fun login(@Body payload: LoginRequest): LoginResponse

    @GET("/api/farms")
    suspend fun getFarms(): List<FarmDto>
}

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String
)
