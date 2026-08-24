package app.terminalssh.secure.ui

import androidx.compose.ui.graphics.Color
import app.terminalssh.secure.R
import app.terminalssh.secure.model.Environment

val Environment.labelRes: Int
    get() = when (this) {
        Environment.NONE -> R.string.env_none
        Environment.DEVELOPMENT -> R.string.env_development
        Environment.STAGING -> R.string.env_staging
        Environment.PRODUCTION -> R.string.env_production
    }

/**
 * Colour is a redundant cue here, never the only one: the band always sits next to the
 * environment's name, so this still reads for a colour-blind user.
 */
val Environment.color: Color?
    get() = when (this) {
        Environment.NONE -> null
        Environment.DEVELOPMENT -> Color(0xFF4F9CF0)
        Environment.STAGING -> Color(0xFFE0A106)
        Environment.PRODUCTION -> Color(0xFFE0523F)
    }
