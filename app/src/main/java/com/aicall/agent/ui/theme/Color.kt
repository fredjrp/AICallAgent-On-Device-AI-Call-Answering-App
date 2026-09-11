package com.aicall.agent.ui.theme

import androidx.compose.ui.graphics.Color

// ── Voice Agent Front Desk — Sage/Mint Theme (from Design HTML) ──────────────
// Primary Brand Accents
val GreenPrimary      = Color(0xFF4CAF7D)   // --green: #4CAF7D
val GreenDeep         = Color(0xFF2E7D56)   // --green-deep: #2E7D56
val GreenSoft         = Color(0xFF8FCFA8)   // --green-soft: #8FCFA8
val GreenDark         = Color(0xFF14402A)   // Heavy dark forest green

// Surface & Background Gradients
val BgTop             = Color(0xFFF4F8F2)   // Top warm off-white canvas
val BgMid             = Color(0xFFEDF3EA)   // Mid off-white
val BgBottom          = Color(0xFFE3EBE0)   // Lower subtle mint-grey
val SurfaceCard       = Color(0xFFFFFFFF)   // Elevated white cards
val SurfaceTranslucent= Color(0xA6FFFFFF)   // rgba(255, 255, 255, 0.65)
val SurfacePill       = Color(0xB8FFFFFF)   // rgba(255, 255, 255, 0.72)
val SurfaceOverlay    = Color(0xFFE8EFE5)   // Light tint for grouped rows/chips

// Typography Colors
val TextPrimary       = Color(0xFF0F1A12)   // Deep forest near-black (--text)
val TextSecondary     = Color(0xFF2C3B31)   // Dark slate green (--text-2)
val TextMuted         = Color(0xFF7A8C80)   // Medium muted green-grey (--muted)
val TextTertiary      = Color(0xFF90A396)   // Lighter timestamps / mono
val TextOnGradient    = Color(0xFFFFFFFF)

// Semantic Accents
val ColorActive       = GreenPrimary
val ColorInactive     = Color(0xFFE0574F)   // --red: #E0574F
val ColorWarning      = Color(0xFFD9963D)   // --amber: #D9963D
val ColorInfo         = Color(0xFF3B82F6)

// Borders & Lines
val LineLight         = Color(0x0F1E3C28)   // rgba(30,60,40, 0.06)
val LineMedium        = Color(0x171E3C28)   // rgba(30,60,40, 0.09)
val BorderLight       = LineLight
val BorderMedium      = LineMedium
val SurfaceCanvas     = BgTop

// Backward-compatibility aliases for earlier components
val BrandPurple       = GreenDeep
val BrandCyan         = GreenPrimary
val BrandMid          = GreenSoft
