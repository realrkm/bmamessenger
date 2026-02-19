package com.example.bmamessenger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.bmamessenger.R

val MozillaHeadline = FontFamily(
    Font(R.font.mozilla_headline, FontWeight.Normal),
    Font(R.font.mozilla_headline, FontWeight.Bold)
)

val AppTypography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(fontFamily = MozillaHeadline),
        bodyMedium = bodyMedium.copy(fontFamily = MozillaHeadline),
        titleLarge = titleLarge.copy(fontFamily = MozillaHeadline),
        titleMedium = titleMedium.copy(fontFamily = MozillaHeadline),
        labelLarge = labelLarge.copy(fontFamily = MozillaHeadline)
    )
}
