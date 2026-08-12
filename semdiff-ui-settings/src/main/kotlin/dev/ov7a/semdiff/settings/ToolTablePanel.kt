package dev.ov7a.semdiff.settings

import com.intellij.openapi.ui.Messages
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import dev.ov7a.semdiff.ide.SemanticDiffSettings
import dev.ov7a.semdiff.ide.ToolEntry
import dev.ov7a.semdiff.tools.HandlerRegistry
import javax.swing.JComponent
import javax.swing.ListSelectionModel

/**
 * The configured-tools table.
 *
 * Works on copies and only writes back in [apply], so Cancel in the settings dialog really cancels.
 */
internal class ToolTablePanel {

    private val model = ListTableModel<ToolEntry>(
        ActiveColumn(),
        readOnlyColumn("Name") { it.name.orEmpty() },
        readOnlyColumn("Tool") { entry -> HandlerRegistry.byId(entry.handlerId.orEmpty())?.displayName ?: "Unknown" },
        readOnlyColumn("Version") { it.detectedVersion.orEmpty() },
        readOnlyColumn("Path") { it.executablePath.orEmpty() },
    )

    private val table = JBTable(model).apply {
        setShowGrid(false)
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        columnModel.getColumn(0).apply {
            maxWidth = 60
            preferredWidth = 60
        }
    }

    private var activeName: String = ""

    val component: JComponent = ToolbarDecorator.createDecorator(table)
        .setAddAction { edit(ToolEntry(), isNew = true) }
        .setEditAction { selected()?.let { edit(it, isNew = false) } }
        .setRemoveActionUpdater { selected() != null }
        .setRemoveAction {
            selected()?.let { entry ->
                model.removeRow(model.indexOf(entry))
                if (activeName == entry.name) activeName = model.items.firstOrNull()?.name.orEmpty()
            }
        }
        .disableUpDownActions()
        .createPanel()

    fun isModified(settings: SemanticDiffSettings): Boolean =
        activeName != settings.activeToolName.orEmpty() || !sameTools(model.items, settings.tools)

    fun apply(settings: SemanticDiffSettings) {
        settings.tools = model.items.map { it.copyEntry() }.toMutableList()
        settings.activeToolName = activeName
    }

    fun reset(settings: SemanticDiffSettings) {
        model.items = settings.tools.map { it.copyEntry() }
        activeName = settings.activeToolName.orEmpty()
        if (model.items.none { it.name == activeName }) {
            activeName = model.items.firstOrNull()?.name.orEmpty()
        }
    }

    private fun selected(): ToolEntry? = table.selectedRow.takeIf { it >= 0 }?.let(model::getItem)

    private fun edit(entry: ToolEntry, isNew: Boolean) {
        val editable = entry.copyEntry()
        if (!ToolEditorDialog(editable).showAndGet()) return

        val name = editable.name.orEmpty()
        if (name.isBlank()) {
            Messages.showErrorDialog(component, "A tool needs a name.", "Semantic Diff")
            return
        }
        val clash = model.items.any { it.name == name && (isNew || it !== entry) }
        if (clash) {
            Messages.showErrorDialog(component, "A tool named '$name' already exists.", "Semantic Diff")
            return
        }

        if (isNew) {
            model.addRow(editable)
            if (activeName.isBlank()) activeName = name
        } else {
            val index = model.indexOf(entry)
            if (activeName == entry.name) activeName = name
            model.setItem(index, editable)
        }
    }

    private fun sameTools(left: List<ToolEntry>, right: List<ToolEntry>): Boolean =
        left.size == right.size && left.zip(right).all { (a, b) -> a.describe() == b.describe() }

    private fun readOnlyColumn(title: String, value: (ToolEntry) -> String) =
        object : ColumnInfo<ToolEntry, String>(title) {
            override fun valueOf(item: ToolEntry): String = value(item)
        }

    /** A single-selection "which tool is used" column; clicking a row's box makes it active. */
    private inner class ActiveColumn : ColumnInfo<ToolEntry, Boolean>("Active") {
        override fun getColumnClass(): Class<*> = Boolean::class.javaObjectType

        override fun valueOf(item: ToolEntry): Boolean = item.name == activeName

        override fun isCellEditable(item: ToolEntry): Boolean = true

        override fun setValue(item: ToolEntry, value: Boolean) {
            if (value) {
                activeName = item.name.orEmpty()
                model.fireTableDataChanged()
            }
        }
    }
}

internal fun ToolEntry.copyEntry(): ToolEntry = ToolEntry().also { copy ->
    copy.name = name
    copy.handlerId = handlerId
    copy.executablePath = executablePath
    copy.arguments = arguments
    copy.environment = LinkedHashMap(environment)
    copy.detectedVersion = detectedVersion
    copy.handlerPinnedByUser = handlerPinnedByUser
}

internal fun ToolEntry.describe(): String =
    listOf(name, handlerId, executablePath, arguments, detectedVersion, handlerPinnedByUser, environment).toString()
