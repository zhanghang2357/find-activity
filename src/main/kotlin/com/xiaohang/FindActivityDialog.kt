package com.xiaohang
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.text.StyleConstants
import javax.swing.text.StyleContext

class FindActivityDialog(private val project: Project, private val initialInfo: String) : DialogWrapper(project) {

    private val textPane = JTextPane().apply {
        isEditable = false
    }
    private val refreshButton = JButton("Refresh")
    private val copyButton = JButton("Copy")
    private val ssLocalButton = JButton("SS Local Info")
    private val activityInfoProvider = ActivityInfoProvider()

    init {
        title = "Activity Info"
        init()
        refreshButton.addActionListener { refreshInfo() }
        copyButton.addActionListener { copyToClipboard() }
        ssLocalButton.addActionListener { showSSLocalInfo() }
        updateTextPane(initialInfo, isActivityInfo = true)
    }

    private fun refreshInfo() {
        object : Task.Backgroundable(project, "Refreshing Activity Info", false) {
            override fun run(indicator: ProgressIndicator) {
                val info = activityInfoProvider.getActivityInfo()
                ApplicationManager.getApplication().invokeLater {
                    updateTextPane(info, isActivityInfo = true)
                }
            }
        }.queue()
    }
    private fun updateTextPane(info: String, isActivityInfo: Boolean) {
        val doc = textPane.styledDocument
        doc.remove(0, doc.length)  // 清除现有内容

        val defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
        StyleConstants.setFontSize(defaultStyle, 12)

        if (isActivityInfo) {
            val taskStyle = doc.addStyle("task", defaultStyle)
            StyleConstants.setFontSize(taskStyle, 16)
            StyleConstants.setBold(taskStyle, true)

            val activityStyle = doc.addStyle("activity", defaultStyle)
            StyleConstants.setFontSize(activityStyle, 14)

            val fragmentStyle = doc.addStyle("fragment", defaultStyle)
            StyleConstants.setFontSize(fragmentStyle, 12)

            info.lines().forEach { line ->
                when {
                    line.startsWith("Task:") -> doc.insertString(doc.length, "$line\n", taskStyle)
                    line.startsWith("  Activity:") -> doc.insertString(doc.length, "$line\n", activityStyle)
                    line.startsWith("    Fragments:") -> doc.insertString(doc.length, "$line\n", fragmentStyle)
                    line.startsWith("      -") -> doc.insertString(doc.length, "$line\n", fragmentStyle)
                    else -> doc.insertString(doc.length, "$line\n", defaultStyle)
                }
            }
        } else {
            val titleStyle = doc.addStyle("title", defaultStyle)
            StyleConstants.setFontSize(titleStyle, 14)
            StyleConstants.setBold(titleStyle, true)

            val pidStyle = doc.addStyle("pid", defaultStyle)
            StyleConstants.setFontSize(pidStyle, 14)
            StyleConstants.setBold(pidStyle, true)

            val contentStyle = doc.addStyle("content", defaultStyle)

            val labelStyle = doc.addStyle("label", contentStyle)
            StyleConstants.setBold(labelStyle, true)

            val separatorStyle = doc.addStyle("separator", defaultStyle)
            StyleConstants.setForeground(separatorStyle, Color.GRAY)

            // 处理 SS Local 信息
            val parts = info.split("\n\n", limit = 3)

            // 处理 "A. libss-local.so processes:"
            doc.insertString(doc.length, parts[0] + "\n\n", titleStyle)

            if (parts.size > 1) {
                // 处理 "B. Command lines for each process:"
                doc.insertString(doc.length, "Command lines for each process:\n", titleStyle)

                val processes = parts[2].split("-".repeat(100))
                processes.forEachIndexed { index, process ->
                    val lines = process.trim().lines()
                    lines.forEachIndexed { lineIndex, line ->
                        when {
                            lineIndex == 0 && line.startsWith("PID") ->
                                doc.insertString(doc.length, "$line\n", pidStyle)
                            line.startsWith("  Command:") -> {
                                doc.insertString(doc.length, "  Command:", labelStyle)
                                doc.insertString(doc.length, line.substringAfter("Command:") + "\n", contentStyle)
                            }
                            line.contains("Arguments:") || line.contains("Server Info:") || line.contains("Download Info:") -> {
                                val labelEnd = line.indexOf('[')
                                if (labelEnd != -1) {
                                    doc.insertString(doc.length, line.substring(0, labelEnd), labelStyle)
                                    doc.insertString(doc.length, line.substring(labelEnd) + "\n", contentStyle)
                                } else {
                                    doc.insertString(doc.length, "$line\n", contentStyle)
                                }
                            }
                            else ->
                                doc.insertString(doc.length, "$line\n", contentStyle)
                        }
                    }
                    if (index < processes.size - 1) {
                        doc.insertString(doc.length, "-".repeat(50) + "\n", separatorStyle)
                    }
                }
            }
        }
    }

    private fun copyToClipboard() {
        val selection = StringSelection(textPane.text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
    }

    private fun showSSLocalInfo() {
        object : Task.Backgroundable(project, "Getting SS Local Info", false) {
            override fun run(indicator: ProgressIndicator) {
                val ssLocalInfo = activityInfoProvider.getSSLocalProcessInfo()
                ApplicationManager.getApplication().invokeLater {
                    updateTextPane(ssLocalInfo, isActivityInfo = false)
                }
            }
        }.queue()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val scrollPane = JBScrollPane(textPane)
        scrollPane.preferredSize = JBUI.size(1000, 500)
        panel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel()
        buttonPanel.add(refreshButton)
        buttonPanel.add(copyButton)
        buttonPanel.add(ssLocalButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    override fun createActions(): Array<Action> = emptyArray()
}