/*
 * Copyright 2024 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pocketshare

import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * MaterialShapes.Clover4Leaf, adapted from AndroidX MaterialShapes.kt for Views.
 * Uses the official clover4 points, mirrored repetition, rounding and normalization.
 * https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/MaterialShapes.kt
 */
internal object OfficialCloverShape {
    val polygon: RoundedPolygon by lazy {
        val x = floatArrayOf(0.500f, 0.725f)
        val y = floatArrayOf(0.074f, -0.099f)
        val rounding = listOf(CornerRounding.Unrounded, CornerRounding(0.476f))
        val angles = x.indices.map { atan2(y[it] - 0.5f, x[it] - 0.5f) * 180f / PI.toFloat() }
        val distances = x.indices.map {
            val dx = x[it] - 0.5f
            val dy = y[it] - 0.5f
            sqrt(dx * dx + dy * dy)
        }
        val vertices = mutableListOf<Float>()
        val perVertexRounding = mutableListOf<CornerRounding>()
        // Official doRepeat(reps = 4, mirroring = true): eight half-sections.
        repeat(8) { section ->
            x.indices.forEach { index ->
                val i = if (section % 2 == 0) index else x.lastIndex - index
                if (i > 0 || section % 2 == 0) {
                    val degrees = 45f * section +
                        if (section % 2 == 0) angles[i] else 45f - angles[i] + 2 * angles[0]
                    val radians = degrees / 360f * 2 * PI.toFloat()
                    vertices += cos(radians) * distances[i] + 0.5f
                    vertices += sin(radians) * distances[i] + 0.5f
                    perVertexRounding += rounding[i]
                }
            }
        }
        RoundedPolygon(
            vertices = vertices.toFloatArray(),
            perVertexRounding = perVertexRounding,
            centerX = 0.5f,
            centerY = 0.5f,
        ).normalized()
    }
}
