package dev.ov7a.semdiff.ide

import com.intellij.util.messages.Topic

/**
 * Announces that the settings changed, so an open diff can react.
 *
 * Without this the experimental viewer only picked up the setting when a diff window was created,
 * which meant ticking the box appeared to do nothing until you closed and reopened the diff. That
 * caveat was reported as a bug twice, which is a fair reading of it.
 */
interface SemanticDiffSettingsListener {

    fun settingsChanged()

    companion object {
        @JvmField
        val TOPIC: Topic<SemanticDiffSettingsListener> =
            Topic.create("Semantic diff settings", SemanticDiffSettingsListener::class.java)
    }
}
