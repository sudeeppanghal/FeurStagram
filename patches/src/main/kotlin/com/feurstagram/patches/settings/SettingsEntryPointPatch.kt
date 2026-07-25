package com.feurstagram.patches.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val SETTINGS_CLASS = "Lcom/feurstagram/extension/Settings;"

/**
 * Fingerprint for Instagram's main activity: com.instagram.mainactivity.InstagramMainActivity.
 * Tries onResume first, falls back to onCreate.
 */
internal object InstagramMainActivityOnResumeFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/mainactivity/InstagramMainActivity;",
    name = "onResume",
)

internal object InstagramMainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/mainactivity/InstagramMainActivity;",
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
        val targetMethod = runCatching {
            InstagramMainActivityOnResumeFingerprint.method
        }.getOrElse {
            InstagramMainActivityOnCreateFingerprint.method
        }

        targetMethod.apply {
            addInstructions(
                0,
                "invoke-static { p0 }, $SETTINGS_CLASS->installFromActivity(Landroid/app/Activity;)V",
            )
        }
    }
}
