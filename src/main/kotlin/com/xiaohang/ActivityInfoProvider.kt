package com.xiaohang

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import java.io.File
class ActivityInfoProvider {

    private val adbPath: String by lazy {
        findAdbPath()
    }

    private fun findAdbPath(): String {
        val possiblePaths = listOf(
            "/Users/${System.getProperty("user.name")}/Library/Android/sdk/platform-tools/adb",
            "/usr/local/bin/adb",
            "adb" // 如果 adb 在 PATH 中
        )

        for (path in possiblePaths) {
            if (File(path).exists()) {
                return path
            }
        }

        return "adb" // 如果都没找到，返回默认值
    }

    fun getActivityInfo(): String {
        val isWindows = System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")
        val deviceName = runCommand(if (isWindows) "$adbPath devices" else "$adbPath devices")
            .trim().lines().lastOrNull { it.contains("\tdevice") }?.split("\t")?.firstOrNull() ?: "No device found"
        
        val activityInfo = runCommand("$adbPath shell dumpsys activity top")
        val formattedInfo = formatActivityInfo(activityInfo)
        return "Device: $deviceName\nADB Path: $adbPath\n\n$formattedInfo"
    }

    private fun formatActivityInfo(info: String): String {
        val lines = info.lines()
        val result = StringBuilder()
        var currentTask: String? = null
        var currentActivity: String? = null
        val fragments = mutableListOf<String>()
        var isCapturingFragments = false

        for (line in lines) {
            val trimmedLine = line.trim()
            when {
                trimmedLine.startsWith("TASK") -> {
                    if (currentTask != null) {
                        appendCurrentInfo(result, currentTask, currentActivity, fragments)
                    }
                    currentTask = extractTaskId(trimmedLine)
                    currentActivity = null
                    fragments.clear()
                    isCapturingFragments = false
                }
                trimmedLine.startsWith("ACTIVITY") -> {
                    currentActivity = extractActivityName(trimmedLine)
                    isCapturingFragments = false
                }
                trimmedLine == "Added Fragments:" -> {
                    isCapturingFragments = true
                }
                isCapturingFragments && trimmedLine.startsWith("#") -> {
                    val fragmentName = extractFragmentName(trimmedLine)
                    if (fragmentName != null && !shouldSkipFragment(fragmentName)) {
                        fragments.add(fragmentName)
                    }
                }
                trimmedLine.startsWith("View Hierarchy:") -> {
                    isCapturingFragments = false
                }
            }
        }

        appendCurrentInfo(result, currentTask, currentActivity, fragments)
        return result.toString()
    }

    private fun extractTaskId(line: String): String = line.split(" ")[1]

    private fun extractActivityName(line: String): String = line.substringAfter("ACTIVITY ").substringBefore(" ")

    private fun extractFragmentName(line: String): String? {
        val match = Regex("#\\d+: ([\\w.]+)\\{").find(line)
        return match?.groupValues?.get(1)
    }

    private fun shouldSkipFragment(fragmentName: String): Boolean {
        val skippedFragments = listOf("DispatchFragment", "InjectFragment", "ReportFragment")
        return skippedFragments.any { fragmentName.endsWith(it) }
    }

    private fun appendCurrentInfo(result: StringBuilder, task: String?, activity: String?, fragments: List<String>) {
        if (task != null) {
            result.append("Task: $task\n")
            if (activity != null) {
                result.append("  Activity: $activity\n")
                if (fragments.isNotEmpty()) {
                    result.append("    Fragments:\n")
                    fragments.forEach { fragment ->
                        result.append("      - $fragment\n")
                    }
                }
            }
            result.append("\n")
        }
    }

    private fun runCommand(command: String): String {
        return try {
            val process = if (System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")) {
                Runtime.getRuntime().exec(arrayOf("cmd.exe", "/c", command))
            } else {
                Runtime.getRuntime().exec(arrayOf("/bin/sh", "-c", command))
            }
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            output.toString()
        } catch (e: Exception) {
            "Error executing command: $command\n${e.message}"
        }
    }
}