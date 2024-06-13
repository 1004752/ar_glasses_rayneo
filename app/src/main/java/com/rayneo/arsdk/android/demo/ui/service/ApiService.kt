package com.rayneo.arsdk.android.demo.ui.service

import retrofit2.http.GET
import com.rayneo.arsdk.android.demo.ui.entity.GolfCourseResponse
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private val retrofit = Retrofit.Builder()
    .baseUrl("https://api.vworld.kr")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

    val golfCourceService = retrofit.create(ApiService::class.java)

interface ApiService {
    @GET("/req/data?service=data&version=2.0&request=GetFeature&format=json&errorformat=json&size=10&page=1&data=LT_P_SGISGOLF&geomfilter=BOX(14109562.483035,4462098.9685774,14110785.475487,4463321.9610298)&attrfilter=golf_name:=:링크나인골프클럽&columns=golf_name,ag_geom&geometry=true&attribute=true&crs=EPSG:900913&key=814270A2-3CDC-3BAA-A6F2-81F54C823AE4&domain=")
    suspend fun getGolfCourses():GolfCourseResponse
}

val golfCourseService: ApiService = retrofit.create(ApiService::class.java)