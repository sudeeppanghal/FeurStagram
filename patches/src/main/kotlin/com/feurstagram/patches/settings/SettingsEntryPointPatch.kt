package com.feurstagram.patches.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val SETTINGS_CLASS = "Lcom/feurstagram/extension/Settings;"

// ─── MainTabActivity.onResume ────────────────────────────────────────────────
// MainTabActivity is Instagram's main launcher activity and is NEVER obfuscated.
// Its onResume() runs every time the user opens or switches back to Instagram.
// We inject a call to Settings.installFromActivity(this) at index 0 of onResume().
// Since onResume() always has p0 (this = Activity), this single-register call is
// 100% reliable across ALL Instagram versions without needing obfuscated fingerprints.
internal object MainTabActivityOnResumeFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/mainactivity/MainTabActivity;",
    name = "onResume",
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
        MainTabActivityOnResumeFingerprint.method.apply {
            addInstructions(
                0,
                "invoke-static { p0 }, $SETTINGS_CLASS->installFromActivity(Landroid/app/Activity;)V",
            )
        }
    }
}
