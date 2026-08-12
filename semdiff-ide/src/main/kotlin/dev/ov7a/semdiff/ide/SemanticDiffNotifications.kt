package dev.ov7a.semdiff.ide

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports tool failures once per session per reason.
 *
 * A broken tool would otherwise produce a balloon on every rediff — including every keystroke in
 * an editable diff — which is worse than the failure it reports.
 */
object SemanticDiffNotifications {

    private const val GROUP_ID = "Semantic Diff"
    private val LOG = Logger.getInstance(SemanticDiffNotifications::class.java)
    private val reported = ConcurrentHashMap.newKeySet<String>()

    fun reportFallback(project: Project?, toolName: String, reason: String) {
        if (!reported.add("$toolName: $reason")) return
        LOG.info("Semantic diff fell back to the built-in diff: $toolName: $reason")

        // Reporting a failure must never become a second failure: this runs inside the diff
        // computation, and the group is absent in tests and in a partially loaded plugin.
        val manager = NotificationGroupManager.getInstance()
        if (!manager.isGroupRegistered(GROUP_ID)) {
            LOG.warn("Notification group '$GROUP_ID' is not registered")
            return
        }

        manager.getNotificationGroup(GROUP_ID)
            .createNotification(
                "Semantic diff unavailable",
                "$toolName could not produce a diff, so the built-in diff was used.\n$reason",
                NotificationType.WARNING,
            )
            .addAction(OpenSettingsAction())
            .notify(project)
    }

    /** Test support: forget what has already been reported. */
    fun resetForTests() = reported.clear()

    private class OpenSettingsAction : DumbAwareAction("Open Settings") {
        override fun actionPerformed(e: AnActionEvent) {
            ShowSettingsUtil.getInstance().showSettingsDialog(e.project, SEMANTIC_DIFF_CONFIGURABLE_NAME)
        }
    }

}

/** Display name of the settings page, shared so the notification action can open it. */
const val SEMANTIC_DIFF_CONFIGURABLE_NAME: String = "Semantic Diff"
