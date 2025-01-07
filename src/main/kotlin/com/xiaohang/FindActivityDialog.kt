package com.xiaohang

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*

class FindActivityDialog(private val project: Project, private val initialInfo: String) : DialogWrapper(project) {

    companion object {
        private const val FONT_SIZE_KEY = "com.xiaohang.findactivity.fontsize"
        private const val DEFAULT_FONT_SIZE = 14
    }

    private val textArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font(font.family, font.style, getFontSize())
    }
    private val refreshButton = JButton("Refresh")
    private val copyButton = JButton("Copy")
    private val activityInfoProvider = ActivityInfoProvider()

    private val fontSizeComboBox = ComboBox(arrayOf(12, 14, 16, 18, 20)).apply {
        selectedItem = getFontSize()
        addActionListener {
            val selectedSize = selectedItem as? Int ?: DEFAULT_FONT_SIZE
            textArea.font = Font(textArea.font.family, textArea.font.style, selectedSize)
            saveFontSize(selectedSize)
        }
    }
    init {
        title = "Activity Info"
        init()
        refreshButton.addActionListener { refreshInfo() }
        copyButton.addActionListener { copyToClipboard() }
        updateTextArea(initialInfo)
    }

    private fun refreshInfo() {
        object : Task.Backgroundable(project, "Refreshing Activity Info", false) {
            override fun run(indicator: ProgressIndicator) {
                val info = activityInfoProvider.getActivityInfo()
                ApplicationManager.getApplication().invokeLater {
                    updateTextArea(info)
                }
            }
        }.queue()
    }

    private fun updateTextArea(info: String) {
        textArea.text = info
    }

    private fun copyToClipboard() {
        val selection = StringSelection(textArea.text)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        val scrollPane = JBScrollPane(textArea)
        scrollPane.preferredSize = JBUI.size(700, 500)
        panel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel()
        buttonPanel.add(refreshButton)
        buttonPanel.add(copyButton)
        buttonPanel.add(JLabel("Font Size:"))
        buttonPanel.add(fontSizeComboBox)
        panel.add(buttonPanel, BorderLayout.SOUTH)


        return panel
    }
    override fun createActions(): Array<Action> = emptyArray()

    private fun getFontSize(): Int {
        return PropertiesComponent.getInstance(project).getInt(FONT_SIZE_KEY, DEFAULT_FONT_SIZE)
    }

    private fun saveFontSize(size: Int) {
        PropertiesComponent.getInstance(project).setValue(FONT_SIZE_KEY, size, DEFAULT_FONT_SIZE)
    }
}