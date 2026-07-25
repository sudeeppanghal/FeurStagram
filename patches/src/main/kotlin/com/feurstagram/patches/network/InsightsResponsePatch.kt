package com.feurstagram.patches.network

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val MOCKER_CLASS = "Lcom/feurstagram/extension/InsightsMocker;"

/**
 * Insights response interception patch.
 *
 * Hooks Instagram's TigonServiceLayer.startRequest (the same method as
 * NetworkBlockPatch) and injects a call to InsightsMocker AFTER the URI
 * iget-object load — but we add a second injection point at the byte-array
 * read to intercept response bodies.
 *
 * Strategy: TigonServiceLayer's startRequest passes the request URI. We hook
 * the network response by finding the IGET_OBJECT that loads a [B (byte array)
 * field and redirecting it through our mocker. If no [B field exists in this
 * method, we fall back to hooking a string constant that marks the response
 * processing path ("response_body" or similar).
 *
 * Since the Morphe fingerprint system resolves the method at patch time using
 * the real Instagram dex, we only need a stable anchor — the class name and
 * any of the string literals that appear near response handling.
 */

// ─── Fingerprint: response body handler ──────────────────────────────────────

/**
 * Fingerprint for the method that receives the HTTP response body.
 * We match on the containing class and a parameter of type [B (byte array).
 *
 * If Instagram changes this method signature, the fallback fingerprint below
 * catches it via the stable "response_body" log string.
 */
internal object TigonResponseHandlerFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    parameters = listOf("[B"),
)

/**
 * Fallback: matches any method in TigonServiceLayer that deals with a byte
 * array parameter — handles minor obfuscation changes.
 */
internal object TigonResponseCallbackFingerprint : Fingerprint(
    strings = listOf("response_body"),
)

// ─── Patch ────────────────────────────────────────────────────────────────────

@Suppress("unused")
val insightsResponsePatch = bytecodePatch(
    name = "Insights response interceptor",
    description = "Intercepts Instagram insights/analytics API responses before JSON parsing " +
        "and replaces metric values with user-defined local overrides. " +
        "Meta servers are never written to — only the in-process display bytes change.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    extendWith(EXTENSION)

    execute {
        // Try the primary fingerprint; fall back gracefully
        val targetMethod = runCatching {
            TigonResponseHandlerFingerprint.method
        }.getOrElse {
            TigonResponseCallbackFingerprint.method
        }

        targetMethod.apply {
            // Find the register that holds the URI (same as NetworkBlockPatch)
            val uriLoad = instructions.firstOrNull {
                it.opcode == Opcode.IGET_OBJECT &&
                    ((it as? ReferenceInstruction)?.reference as? FieldReference)?.type == "Ljava/net/URI;"
            }

            // Find the register that holds the [B (byte array) response body
            val byteArrayLoad = instructions.firstOrNull {
                it.opcode == Opcode.IGET_OBJECT &&
                    ((it as? ReferenceInstruction)?.reference as? FieldReference)?.type == "[B"
            }

            // Determine injection point and registers
            if (byteArrayLoad != null) {
                val bodyRegister = (byteArrayLoad as OneRegisterInstruction).registerA
                val uriRegister = if (uriLoad != null) {
                    (uriLoad as OneRegisterInstruction).registerA
                } else {
                    bodyRegister + 1
                }

                // Inject right after the byte-array is loaded into its register.
                // We call InsightsMocker.interceptResponse(uriString, body) -> body
                // and put the result back into the same register.
                val injectIndex = byteArrayLoad.location.index + 1

                addInstructions(
                    injectIndex,
                    // Get the URI as a String path
                    "invoke-virtual/range { v$uriRegister .. v$uriRegister }, " +
                        "Ljava/net/URI;->getPath()Ljava/lang/String;\n" +
                        "move-result-object v${uriRegister + 2}\n" +
                        // Intercept: (String path, byte[] body) -> byte[]
                        "invoke-static { v${uriRegister + 2}, v$bodyRegister }, " +
                        "$MOCKER_CLASS->interceptResponse(Ljava/lang/String;[B)[B\n" +
                        "move-result-object v$bodyRegister",
                )
            } else if (uriLoad != null) {
                // Fallback: no byte array field found in this method.
                // Inject right after the URI load at index 0 — the mocker will
                // attempt to match based on the path alone when called from a
                // request-level hook (limited functionality but safe).
                val uriRegister = (uriLoad as OneRegisterInstruction).registerA
                val injectIndex = uriLoad.location.index + 1

                addInstructions(
                    injectIndex,
                    "invoke-virtual/range { v$uriRegister .. v$uriRegister }, " +
                        "Ljava/net/URI;->getPath()Ljava/lang/String;\n" +
                        "move-result-object v${uriRegister + 1}\n" +
                        // Call with null body — mocker returns null safely
                        "const/4 v${uriRegister + 2}, 0x0\n" +
                        "invoke-static { v${uriRegister + 1}, v${uriRegister + 2} }, " +
                        "$MOCKER_CLASS->interceptResponse(Ljava/lang/String;[B)[B\n" +
                        "move-result-object v${uriRegister + 2}",
                )
            }
            // If neither found, patch does nothing (safe no-op)
        }
    }
}
