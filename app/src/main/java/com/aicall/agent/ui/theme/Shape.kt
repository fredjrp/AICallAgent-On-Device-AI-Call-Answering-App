package com.aicall.agent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    // Cards, large surfaces
    extraLarge = RoundedCornerShape(24.dp),
    // Standard cards
    large      = RoundedCornerShape(16.dp),
    // Input fields, smaller cards
    medium     = RoundedCornerShape(12.dp),
    // Chips, small badges
    small      = RoundedCornerShape(8.dp),
    // Inline tags
    extraSmall = RoundedCornerShape(4.dp)
)
