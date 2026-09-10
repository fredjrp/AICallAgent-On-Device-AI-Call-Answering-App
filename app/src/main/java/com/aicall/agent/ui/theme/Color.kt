package com.aicall.agent.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand Gradient ──────────────────────────────────────────────────────────
val BrandPurple   = Color(0xFF8950E5)   // Deep Purple — gradient start
val BrandCyan     = Color(0xFF00B6F9)   // Bright Cyan  — gradient end
val BrandMid      = Color(0xFF4E89F0)   // Midpoint for 3-stop gradients

// ── Semantic Status ──────────────────────────────────────────────────────────
val ColorActive   = Color(0xFF34C759)   // iOS-green  — AI Enabled
val ColorInactive = Color(0xFFFF3B30)   // iOS-red    — AI Disabled / missed
val ColorWarning  = Color(0xFFFF9500)   // iOS-orange — partial / warning
val ColorInfo     = Color(0xFF007AFF)   // iOS-blue   — informational

// ── Light Surface Palette ────────────────────────────────────────────────────
val SurfaceCanvas   = Color(0xFFF9FAFB) // Off-white page background
val SurfaceCard     = Color(0xFFFFFFFF) // Pure white elevated cards
val SurfaceOverlay  = Color(0xFFF2F2F7) // iOS-style grouped table background
val SurfacePressed  = Color(0xFFE8E8ED) // Pressed / ripple state

// ── Typography Colors ────────────────────────────────────────────────────────
val TextPrimary    = Color(0xFF111827)  // Near-black heading
val TextSecondary  = Color(0xFF6B7280)  // Medium-grey subtext
val TextTertiary   = Color(0xFF9CA3AF)  // Light-grey timestamps / labels
val TextOnGradient = Color(0xFFFFFFFF)  // White on gradient surfaces

// ── Divider / Border ─────────────────────────────────────────────────────────
val BorderLight    = Color(0xFFE5E7EB)  // 1dp separator line
val BorderMedium   = Color(0xFFD1D5DB)

// ── Visualizer States ────────────────────────────────────────────────────────
val VisualizerIdle      = Color(0xFFBDBDBD)
val VisualizerListening = BrandPurple
val VisualizerResponding= BrandCyan
