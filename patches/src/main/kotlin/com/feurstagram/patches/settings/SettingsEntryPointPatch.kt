package com.feurstagram.patches.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val SETTINGS_CLASS = "Lcom/feurstagram/extension/Settings;"

private fun fieldType(instruction: Any?): String? =
    ((instruction as? ReferenceInstruction)?.reference as? FieldReference)?.type

// ─── Primary: obfuscated tab-bar binder (any prefix) ─────────────────────────
// The main tab-bar binder is an obfuscated class whose constructor takes a View,
// stores a ViewGroup field and a View field via IPUT_OBJECT. We match the shape
// (not the obfuscated class name) because Instagram reshuffles names each release.
// We broaden the prefix match from just "LX/" to any single-letter namespace so
// it survives between version bumps.
internal object TabBarBinderFingerprint : Fingerprint(
    name = "<init>",
    parameters = listOf("Landroid/view/View;"),
    custom = { method, classDef ->
        // Single-letter obfuscated namespace: LX/, LA/, LB/, etc.
        val type = classDef.type
        val isSingleLetterNs = type.length > 3 &&
            type[0] == 'L' &&
            type[1].isUpperCase() &&
            type[2] == '/'
        isSingleLetterNs &&
            method.implementation?.instructions?.let { instructions ->
                var hasViewGroupField = false
                var hasViewField = false
                for (instruction in instructions) {
                    if (instruction.opcode == Opcode.IPUT_OBJECT) {
                        when (fieldType(instruction)) {
                            "Landroid/view/ViewGroup;" -> hasViewGroupField = true
                            "Landroid/view/View;" -> hasViewField = true
                        }
                    }
                }
                hasViewGroupField && hasViewField
            } == true
    },
)

// ─── Fallback: MainTabActivity.onResume ──────────────────────────────────────
// MainTabActivity is never obfuscated; its onResume() fires whenever the user
// returns to the main screen. We use it as a guaranteed fallback to call
// installHomeTabWatcher, which internally uses ViewTreeObserver to find the
// tab bar once it's laid out.
internal object MainTabActivityOnResumeFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/mainactivity/MainTabActivity;",
    name = "onResume",
)

@Suppress("unused")
val settingsEntryPointPatch = bytecodePatch(
    name = "Settings entry point",
    description = "Opens the Feurstagram settings on a long-press of the Home tab, " +
        "and installs the surface hiders and update check.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    extendWith(EXTENSION)

    execute {
        // ── Try the primary tab-bar binder fingerprint first ──────────────
        val primarySuccess = runCatching {
            TabBarBinderFingerprint.method.apply {
                val tabBarStore = instructions.first {
                    it.opcode == Opcode.IPUT_OBJECT && fieldType(it) == "Landroid/view/ViewGroup;"
                }
                val tabBarRegister = (tabBarStore as TwoRegisterInstruction).registerA

                addInstructions(
                    tabBarStore.location.index + 1,
                    "invoke-static { v$tabBarRegister }, " +
                        "$SETTINGS_CLASS->installHomeTabWatcher(Landroid/view/ViewGroup;)V",
                )
            }
        }.isSuccess

        if (primarySuccess) return@execute  // primary worked, done

        // ── Fallback: hook MainTabActivity.onResume ───────────────────────
        // onResume gives us the Activity context. We call a new overload
        // installFromActivity(Activity) which posts a Runnable on the
        // activity's root view to find the tab bar after layout.
        MainTabActivityOnResumeFingerprint.method.apply {
            // Insert at index 0 — before any existing onResume logic — so
            // the watcher is always registered regardless of super calls.
            addInstructions(
                0,
                "invoke-virtual { p0 }, Landroid/app/Activity;->getWindow()Landroid/view/Window;\n" +
                    "move-result-object v0\n" +
                    "invoke-virtual { v0 }, Landroid/view/Window;->getDecorView()Landroid/view/View;\n" +
                    "move-result-object v0\n" +
                    "invoke-static { v0 }, $SETTINGS_CLASS->installFromDecorView(Landroid/view/View;)V",
            )
        }
    }
}
