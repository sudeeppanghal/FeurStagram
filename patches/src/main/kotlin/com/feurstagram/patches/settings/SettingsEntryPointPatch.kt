package com.feurstagram.patches.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val SETTINGS_CLASS = "Lcom/feurstagram/extension/Settings;"

/**
 * Fingerprint for Instagram's Application shell: com.instagram.app.InstagramAppShell.
 * The onCreate() method runs immediately when Instagram launches.
 */
internal object InstagramAppShellFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/app/InstagramAppShell;",
    name = "onCreate",
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
        InstagramAppShellFingerprint.method.apply {
            addInstructions(
                0,
                "invoke-static { p0 }, $SETTINGS_CLASS->init(Landroid/app/Application;)V",
            )
        }
    }
}
