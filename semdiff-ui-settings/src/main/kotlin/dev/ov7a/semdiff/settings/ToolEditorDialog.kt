package dev.ov7a.semdiff.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import dev.ov7a.semdiff.ide.SemanticDiffService
import dev.ov7a.semdiff.ide.ToolEntry
import dev.ov7a.semdiff.tools.HandlerRegistry
import dev.ov7a.semdiff.tools.SemanticDiffToolHandler
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants
import javax.swing.JScrollPane

/**
 * Add/edit dialog for one configured tool.
 *
 * Detect and Test both talk to the real binary — the point of this dialog is to answer "will this
 * actually work?" before the user finds out in a diff window.
 */
internal class ToolEditorDialog(private val entry: ToolEntry) : DialogWrapper(true) {

    private val nameField = JBTextField(entry.name.orEmpty())
    private val pathField = TextFieldWithBrowseButton().apply { text = entry.executablePath.orEmpty() }
    private val argumentsField = JBTextField(entry.arguments.orEmpty())
    private val environmentField = JBTextField(entry.environment.entries.joinToString(" ") { "${it.key}=${it.value}" })
    private val output = JBTextArea(10, 80).apply {
        isEditable = false
        lineWrap = false
    }

    private var handler: SemanticDiffToolHandler? = HandlerRegistry.byId(entry.handlerId.orEmpty())
    private var handlerPinned: Boolean = entry.handlerPinnedByUser
    private var detectedVersion: String = entry.detectedVersion.orEmpty()

    init {
        title = "Semantic Diff Tool"
        pathField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withTitle("Select Diff Tool Executable"),
        )
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row("Name:") { cell(nameField).align(AlignX.FILL) }
        row("Executable:") { cell(pathField).align(AlignX.FILL) }

        row("Parser:") {
            comboBox(HandlerRegistry.all.map { it.displayName } + UNKNOWN_HANDLER)
                .bindItem(
                    { handler?.displayName ?: UNKNOWN_HANDLER },
                    { selection ->
                        val chosen = HandlerRegistry.all.firstOrNull { it.displayName == selection }
                        if (chosen != handler) {
                            handler = chosen
                            handlerPinned = chosen != null
                        }
                    },
                )
            button("Detect") { detect() }
        }

        row("Arguments:") {
            cell(argumentsField).align(AlignX.FILL)
                .comment("%1 = left file, %2 = right file, %3 = base file")
        }
        row("Environment:") {
            cell(environmentField).align(AlignX.FILL)
                .comment("Space-separated NAME=VALUE pairs.")
        }

        row {
            button("Test") { test() }
        }

        row {
            cell(
                JScrollPane(output).apply {
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                },
            ).align(AlignX.FILL)
        }
    }

    override fun doValidate(): ValidationInfo? = when {
        nameField.text.isBlank() -> ValidationInfo("Give the tool a name.", nameField)
        pathField.text.isBlank() -> ValidationInfo("Point at the tool's executable.", pathField.textField)
        handler == null -> ValidationInfo("Pick a parser, or press Detect.", pathField.textField)
        else -> null
    }

    override fun doOKAction() {
        entry.name = nameField.text.trim()
        entry.executablePath = pathField.text.trim()
        entry.handlerId = handler?.id.orEmpty()
        entry.arguments = argumentsField.text.trim().ifBlank { handler?.defaultArgumentPattern.orEmpty() }
        entry.environment = parseEnvironment(environmentField.text).toMutableMap()
        entry.detectedVersion = detectedVersion
        entry.handlerPinnedByUser = handlerPinned
        super.doOKAction()
    }

    private fun detect() {
        val report = ToolProbe.detect(pathField.text.trim())
        output.text = report.log

        if (report.handler != null && !handlerPinned) {
            handler = report.handler
            detectedVersion = report.version.orEmpty()
            if (argumentsField.text.isBlank()) argumentsField.text = report.handler.defaultArgumentPattern
            if (environmentField.text.isBlank()) {
                environmentField.text = report.handler.defaultEnvironment.entries.joinToString(" ") {
                    "${it.key}=${it.value}"
                }
            }
        }
    }

    private fun test() {
        val candidate = ToolEntry().also {
            it.name = nameField.text.trim()
            it.handlerId = handler?.id.orEmpty()
            it.executablePath = pathField.text.trim()
            it.arguments = argumentsField.text.trim()
            it.environment = parseEnvironment(environmentField.text).toMutableMap()
        }

        val invocation = SemanticDiffService.instance.invocationFor(candidate)
        if (invocation == null) {
            output.text = "Cannot test yet: pick a parser and an executable first."
            return
        }

        var report = ""
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            { report = ToolProbe.test(invocation) },
            "Testing ${candidate.name.orEmpty().ifBlank { "Diff Tool" }}",
            true,
            null,
            contentPanel,
        )
        output.text = report
    }

    private fun parseEnvironment(text: String): Map<String, String> =
        text.split(' ')
            .filter { it.contains('=') }
            .associate { pair -> pair.substringBefore('=') to pair.substringAfter('=') }

    private companion object {
        const val UNKNOWN_HANDLER = "Not detected"
    }
}
