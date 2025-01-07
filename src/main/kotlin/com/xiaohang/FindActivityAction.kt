package com.xiaohang

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

class FindActivityAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        object : Task.Backgroundable(project, "Finding Activity", false) {
            override fun run(indicator: ProgressIndicator) {
                val info = ActivityInfoProvider().getActivityInfo()
                ApplicationManager.getApplication().invokeLater {
                    FindActivityDialog(project, info).show()
                }
            }
        }.queue()
    }
}