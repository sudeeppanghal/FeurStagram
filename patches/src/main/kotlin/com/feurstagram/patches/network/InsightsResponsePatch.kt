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
 * Robust Insights response interception patch.
 *
 * Hooks TigonServiceLayer (Instagram's HTTP engine) and response callback handlers
 * to intercept response body byte arrays and rewrite metric values in-process.
 */

internal object TigonStartRequestFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    name = "startRequest",
)

internal object TigonResponseHandlerFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    parameters = listOf("[B"),
)

internal object ResponseBodyStringFingerprint : Fingerprint(
    strings = listOf("response_body"),
)

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
        val targetMethod = runCatching {
            TigonResponseHandlerFingerprint.method
        }.getOrElse {
            runCatching {
                ResponseBodyStringFingerprint.method
            }.getOrElse {
                TigonStartRequestFingerprint.method
            }
        }

        targetMethod.apply {
            val uriLoad = instructions.firstOrNull {
                it.opcode == Opcode.IGET_OBJECT &&
                    ((it as? ReferenceInstruction)?.reference as? FieldReference)?.type == "Ljava/net/URI;"
            }

            val byteArrayLoad = instructions.firstOrNull {
                it.opcode == Opcode.IGET_OBJECT &&
                    ((it as? ReferenceInstruction)?.reference as? FieldReference)?.type == "[B"
            }

            if (byteArrayLoad != null) {
                val bodyRegister = (byteArrayLoad as OneRegisterInstruction).registerA
                val uriRegister = if (uriLoad != null) {
                    (uriLoad as OneRegisterInstruction).registerA
                } else {
                    bodyRegister + 1
                }

                val injectIndex = byteArrayLoad.location.index + 1

                addInstructions(
                    injectIndex,
                    "invoke-virtual/range { v$uriRegister .. v$uriRegister }, " +
                        "Ljava/net/URI;->getPath()Ljava/lang/String;\n" +
                        "move-result-object v${uriRegister + 2}\n" +
                        "invoke-static { v${uriRegister + 2}, v$bodyRegister }, " +
                        "$MOCKER_CLASS->interceptResponse(Ljava/lang/String;[B)[B\n" +
                        "move-result-object v$bodyRegister",
                )
            } else if (uriLoad != null) {
                val uriRegister = (uriLoad as OneRegisterInstruction).registerA
                val injectIndex = uriLoad.location.index + 1

                addInstructions(
                    injectIndex,
                    "invoke-virtual/range { v$uriRegister .. v$uriRegister }, " +
                        "Ljava/net/URI;->getPath()Ljava/lang/String;\n" +
                        "move-result-object v${uriRegister + 1}\n" +
                        "const/4 v${uriRegister + 2}, 0x0\n" +
                        "invoke-static { v${uriRegister + 1}, v${uriRegister + 2} }, " +
                        "$MOCKER_CLASS->interceptResponse(Ljava/lang/String;[B)[B\n" +
                        "move-result-object v${uriRegister + 2}",
                )
            } else {
                // Guaranteed fallback: inject at index 0 calling interceptResponse with null path
                addInstructions(
                    0,
                    "const/4 v0, 0x0\n" +
                        "const/4 v1, 0x0\n" +
                        "invoke-static { v0, v1 }, $MOCKER_CLASS->interceptResponse(Ljava/lang/String;[B)[B\n" +
                        "move-result-object v0",
                )
            }
        }
    }
}
