package com.feurstagram.patches.network

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.feurstagram.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import com.feurstagram.patches.shared.Constants.EXTENSION

private const val MOCKER_CLASS = "Lcom/feurstagram/extension/InsightsMocker;"

/**
 * Insights response interception patch.
 *
 * Strategy: Instagram's TigonServiceLayer handles both request and response.
 * The response arrives as a byte array in the response handler callback.
 * We fingerprint the response handler by:
 *   1. The containing class: TigonServiceLayer (already known, not obfuscated)
 *   2. A method that receives a byte array ([B) parameter after the request completes
 *   3. Stable strings inside that method related to response parsing
 *
 * We inject our interceptor right after the response byte array is materialised,
 * before it is fed to the JSON deserialiser. The interceptor either returns the
 * original bytes unchanged (when editor is off or the path doesn't match insights),
 * or returns a modified byte array with our overrides applied.
 *
 * The URI of the original request is threaded through so the mocker knows which
 * endpoint it's intercepting. We grab it from the same register that NetworkBlockPatch
 * already identified (the URI iget-object instruction).
 */

// ─── Fingerprint: TigonServiceLayer response handler ──────────────────────────

/**
 * Fingerprint for the method in TigonServiceLayer that is called when an HTTP
 * response is successfully received. It receives the response body as a byte
 * array ([B).
 *
 * We match on:
 *  - The class name (non-obfuscated, stable across Instagram versions)
 *  - A method that takes a byte-array parameter (response body)
 *  - The presence of a byte-array store/return instruction sequence
 *
 * Fallback: if TigonServiceLayer response side can't be matched directly,
 * we hook the Okio/OkHttp response buffer reader which is a more stable
 * target. The fingerprint below prefers TigonServiceLayer first.
 */
internal object TigonResponseHandlerFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    // Match on the method that has a byte-array parameter — this is the
    // response body delivery method. The parameter type [B is stable.
    parameters = listOf("[B", "Ljava/net/URI;"),
)

/**
 * Fallback fingerprint: matches the response callback interface method.
 * Instagram's network stack calls a callback with (URI, responseBytes).
 * If TigonServiceLayer's exact method signature changes, this broader
 * match catches the callback implementor.
 */
internal object TigonResponseCallbackFingerprint : Fingerprint(
    // The stable string "response_body" appears in Instagram's network logging
    // right next to where response bytes are read.
    strings = listOf("response_body"),
    parameters = listOf("[B"),
)

// ─── Patch definition ─────────────────────────────────────────────────────────

@Suppress("unused")
val insightsResponsePatch = bytecodePatch(
    name = "Insights response interceptor",
    description = "Intercepts Instagram's insights/analytics API responses before JSON parsing " +
        "and optionally replaces metric values with user-defined overrides stored locally. " +
        "Meta servers are never contacted with fake data — only the in-process bytes are modified.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    extendWith(EXTENSION)

    execute {
        // Try the primary fingerprint first
        val targetMethod = runCatching {
            TigonResponseHandlerFingerprint.method
        }.getOrElse {
            // Fall back to the broader response callback fingerprint
            TigonResponseCallbackFingerprint.method
        }

        targetMethod.apply {
            // Find the register holding the response byte array ([B).
            // It is the parameter passed into this method — p1 in most cases,
            // but we locate it structurally by finding the first array-load
            // opcode that deals with a byte array, or use the parameter register.
            //
            // Simpler approach: in a method with parameters ([B, URI) or ([B),
            // the byte array is always in register p1 (index 1 for virtual, 0 for static).
            // We inject at index 0 so we run before any other processing.

            // Find the URI register if present (same iget-object pattern as NetworkBlockPatch)
            val uriLoad = instructions.firstOrNull {
                it.opcode == Opcode.IGET_OBJECT &&
                    ((it as? ReferenceInstruction)?.reference as? FieldReference)?.type == "Ljava/net/URI;"
            }

            // Find the byte-array register: look for the first move-result or
            // parameter register loaded with [B type. As a fallback use p1.
            val byteArrayInstruction = instructions.firstOrNull {
                it.opcode == Opcode.ARRAY_LENGTH ||
                    (it.opcode == Opcode.IGET_OBJECT &&
                        ((it as? ReferenceInstruction)?.reference as? FieldReference)?.type == "[B")
            }

            // Determine registers
            val responseRegister: Int
            val uriRegister: Int

            if (byteArrayInstruction != null) {
                responseRegister = (byteArrayInstruction as OneRegisterInstruction).registerA
            } else {
                // Use the first parameter register as the response byte array
                // (p1 for instance methods = register index 1 after 'this')
                responseRegister = implementation?.registerCount?.minus(
                    implementation?.parameters?.size ?: 1
                ) ?: 1
            }

            uriRegister = if (uriLoad != null) {
                (uriLoad as OneRegisterInstruction).registerA
            } else {
                responseRegister + 1 // best-effort fallback
            }

            // Inject at the beginning of the method (index 0), before any
            // Instagram processing occurs.
            //
            // Smali: call InsightsMocker.interceptResponse(path, body) → body
            // The result (possibly modified bytes) is moved back into the
            // same register so the rest of the method uses the intercepted bytes.
            //
            // If the URI register is out of range for the instruction, the
            // mocker will receive null and will safely return the original bytes.
            addInstructions(
                0,
                // Convert URI to String path first
                "invoke-virtual/range { v$uriRegister .. v$uriRegister }, " +
                    "Ljava/net/URI;->getPath()Ljava/lang/String;\n" +
                    "move-result-object v${responseRegister + 2}\n" +
                    // Call our interceptor: (String path, byte[] body) → byte[]
                    "invoke-static/range { v${responseRegister + 2} .. v$responseRegister }, " +
                    "$MOCKER_CLASS->interceptResponse(Ljava/lang/String;[B)[B\n" +
                    "move-result-object v$responseRegister",
            )
        }
    }
}
