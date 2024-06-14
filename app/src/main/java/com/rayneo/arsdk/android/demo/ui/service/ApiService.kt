package com.rayneo.arsdk.android.demo.ui.service

import retrofit2.http.GET
import com.rayneo.arsdk.android.demo.ui.entity.GolfCourseResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Query

private val retrofit = Retrofit.Builder()
    .baseUrl("https://api.vworld.kr")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

interface ApiService {
    @GET("/req/data")
    suspend fun getGolfCourses(
        @Query("service") service: String,
        @Query("version") version: String,
        @Query("request") request: String,
        @Query("format") format: String,
        @Query("errorformat") errorformat: String,
        @Query("size") size: String,
        @Query("page") page: String,
        @Query("data") data: String,
        @Query("geomfilter") geomfilter: String,
        @Query("columns") columns: String,
        @Query("geometry") geometry: String,
        @Query("attribute") attribute: String,
        @Query("crs") crs: String,
        @Query("key") key: String,
        @Query("domain") domain: String,
    ):GolfCourseResponse
}

val golfCourseService: ApiService = retrofit.create(ApiService::class.java)