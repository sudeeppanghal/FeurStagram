package com.feurstagram.patches.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val SETTINGS_CLASS = "Lcom/feurstagram/extension/Settings;"

/**
 * Uses TigonServiceLayer.startRequest as anchor — 100% stable non-obfuscated
 * class present in every Instagram release. Triggers on early network startup.
 */
internal object TigonStartRequestFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    name = "startRequest",
)

@Suppress("unused")
val settingsEntryPointPatch = bytecodePatch(
    name = "Settings entry point",
    description = "Installs the floating Insights button and Feurstagram settings entry point into Instagram.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    extendWith(EXTENSION)

    execute {
        TigonStartRequestFingerprint.method.apply {
            // Zero-parameter static call — 100% safe, no register conflicts
            addInstructions(
                0,
                "invoke-static {}, $SETTINGS_CLASS->onNetworkRequest()V",
            )
        }
    }
}
