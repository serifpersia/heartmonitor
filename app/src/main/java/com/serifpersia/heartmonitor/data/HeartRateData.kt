package com.serifpersia.heartmonitor.data

import androidx.compose.ui.graphics.Color

data class HeartRateZoneData(val name: String, val range: IntRange, val color: Color)

data class AgeGroupHeartRateData(val ageRange: IntRange, val zones: List<HeartRateZoneData>)

object HeartRateData {
    val ageGroups = listOf(
        AgeGroupHeartRateData(
            ageRange = 20..29,
            zones = listOf(
                HeartRateZoneData("Resting", 0..59, Color(0xFF4FC3F7)),
                HeartRateZoneData("Light", 60..99, Color(0xFF81C784)),
                HeartRateZoneData("Moderate", 100..139, Color(0xFFFFF176)),
                HeartRateZoneData("Hard", 140..169, Color(0xFFFFA726)),
                HeartRateZoneData("Maximum", 170..200, Color(0xFFE57373))
            )
        ),
        AgeGroupHeartRateData(
            ageRange = 30..39,
            zones = listOf(
                HeartRateZoneData("Resting", 0..59, Color(0xFF4FC3F7)),
                HeartRateZoneData("Light", 60..94, Color(0xFF81C784)),
                HeartRateZoneData("Moderate", 95..132, Color(0xFFFFF176)),
                HeartRateZoneData("Hard", 133..161, Color(0xFFFFA726)),
                HeartRateZoneData("Maximum", 162..190, Color(0xFFE57373))
            )
        ),
        AgeGroupHeartRateData(
            ageRange = 40..49,
            zones = listOf(
                HeartRateZoneData("Resting", 0..59, Color(0xFF4FC3F7)),
                HeartRateZoneData("Light", 60..89, Color(0xFF81C784)),
                HeartRateZoneData("Moderate", 90..125, Color(0xFFFFF176)),
                HeartRateZoneData("Hard", 126..152, Color(0xFFFFA726)),
                HeartRateZoneData("Maximum", 153..180, Color(0xFFE57373))
            )
        ),
        AgeGroupHeartRateData(
            ageRange = 50..59,
            zones = listOf(
                HeartRateZoneData("Resting", 0..59, Color(0xFF4FC3F7)),
                HeartRateZoneData("Light", 60..84, Color(0xFF81C784)),
                HeartRateZoneData("Moderate", 85..118, Color(0xFFFFF176)),
                HeartRateZoneData("Hard", 119..144, Color(0xFFFFA726)),
                HeartRateZoneData("Maximum", 145..170, Color(0xFFE57373))
            )
        ),
        AgeGroupHeartRateData(
            ageRange = 60..69,
            zones = listOf(
                HeartRateZoneData("Resting", 0..59, Color(0xFF4FC3F7)),
                HeartRateZoneData("Light", 60..79, Color(0xFF81C784)),
                HeartRateZoneData("Moderate", 80..111, Color(0xFFFFF176)),
                HeartRateZoneData("Hard", 112..135, Color(0xFFFFA726)),
                HeartRateZoneData("Maximum", 136..160, Color(0xFFE57373))
            )
        ),
        AgeGroupHeartRateData(
            ageRange = 70..100,
            zones = listOf(
                HeartRateZoneData("Resting", 0..59, Color(0xFF4FC3F7)),
                HeartRateZoneData("Light", 60..74, Color(0xFF81C784)),
                HeartRateZoneData("Moderate", 75..104, Color(0xFFFFF176)),
                HeartRateZoneData("Hard", 105..127, Color(0xFFFFA726)),
                HeartRateZoneData("Maximum", 128..150, Color(0xFFE57373))
            )
        )
    )
}
