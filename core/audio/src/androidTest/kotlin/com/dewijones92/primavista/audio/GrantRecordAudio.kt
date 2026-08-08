package com.dewijones92.primavista.audio

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** A manifest entry is not a grant. See .claude/CODE-NOTES.md. */
fun grantRecordAudio() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packages = setOf(
        instrumentation.targetContext.packageName,
        instrumentation.context.packageName,
    )
    packages.forEach { name ->
        val output = instrumentation.uiAutomation
            .executeShellCommand("pm grant $name android.permission.RECORD_AUDIO")
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }
}
