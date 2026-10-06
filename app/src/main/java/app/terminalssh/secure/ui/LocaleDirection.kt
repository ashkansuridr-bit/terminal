package app.terminalssh.secure.ui

import android.text.TextUtils
import android.view.View
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** Android's locale/script-aware direction: Arabic and script overrides are not Persian. */
internal fun terminalLayoutDirection(locale: Locale): LayoutDirection =
    if (TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
