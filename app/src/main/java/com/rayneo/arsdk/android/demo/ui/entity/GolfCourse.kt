package com.rayneo.arsdk.android.demo.ui.entity

data class GolfCourse(
    val displayName: String,
    val distance: String,
    val id: Long,

    ) {
    companion object {
        val Invalid = GolfCourse(
            id = -1,
            displayName = "",
            distance = ""
        )
    }
}

data class GolfCourseResponse(
    val response: Response
) {
    data class Response(
        val result: Result
    ) {
        data class Result(
            val featureCollection: FeatureCollection
        ) {
            data class FeatureCollection(
                val features: List<Feature>
            ) {
                data class Feature(
                    val properties: Properties,
                    val geometry: Geometry
                ) {
                    data class Properties(
                        val golf_name: String
                    )
                    data class Geometry(
                        val coordinates: List<Double>
                    )
                }
            }
        }
    }
}