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
    fun getSSLocalProcessInfo(): String {
        val result = StringBuilder()

        // 获取信息A
        val psCommand = if (System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")) {
            "$adbPath shell ps -A | findstr libss-local.so"
        } else {
            "$adbPath shell ps -A | grep libss-local.so"
        }
        val psOutput = runCommand(psCommand)

        result.append("A. libss-local.so processes:\n")
        result.append(psOutput.trim())
        result.append("\n\n")

        // 正确提取第二列作为PID
        val pids = psOutput.lines().mapNotNull { line ->
            line.trim().split("\\s+".toRegex()).getOrNull(1)
        }

        result.append("Command lines for each process:\n")
        pids.forEachIndexed { index, pid ->
            if (index > 0) {
                result.append("-".repeat(100) + "\n")  // 只在进程之间添加分割线
            }
            val cmdlineCommand = "$adbPath shell cat /proc/$pid/cmdline"
            val cmdlineOutput = runCommand(cmdlineCommand).replace("\u0000", " ").trim()
            result.append("PID $pid:\n")
            result.append(formatCommandLine(cmdlineOutput))
            result.append("\n")  // 在每个进程信息后添加一个空行，而不是分隔线
        }

        return result.toString().trimEnd()  // 移除最后可能多余的换行
    }
    private fun formatCommandLine(cmdline: String): String {
        val parts = cmdline.split(" ")
        val formatted = StringBuilder()

        formatted.append("  Command: ${parts[0]}\n\n")

        val arguments = mutableListOf<String>()
        val serverInfo = mutableListOf<String>()
        val downloadInfo = mutableListOf<String>()
        val additionalSettings = mutableListOf<String>()

        var i = 1
        while (i < parts.size) {
            when {
                parts[i].startsWith("--si-") -> {
                    serverInfo.add("${parts[i]} ${parts.getOrElse(i+1) {""}}")
                    i += 2
                }
                parts[i].startsWith("--dl-") -> {
                    downloadInfo.add("${parts[i]} ${parts.getOrElse(i+1) {""}}")
                    i += 2
                }
                parts[i].startsWith("--") -> {
                    if (parts[i] == "--uid") {
                        additionalSettings.add("${parts[i]} [长字符串，已省略]")
                        i += 2
                    } else {
                        additionalSettings.add("${parts[i]} ${parts.getOrElse(i+1) {""}}")
                        i += 2
                    }
                }
                else -> {
                    arguments.add("${parts[i]} ${parts.getOrElse(i+1) {""}}")
                    i += 2
                }
            }
        }

        val labelWidth = 25  // 增加标签宽度
        val space = "\u2007"  // 使用等宽空格字符

        formatted.append("  ${"Arguments:".padEnd(labelWidth, space.single())}[ ${arguments.joinToString(" ")} ]\n")
        if (serverInfo.isNotEmpty()) {
            formatted.append("  ${"Server Info:".padEnd(labelWidth, space.single())}[ ${serverInfo.joinToString(" ")} ]\n")
        }
        if (downloadInfo.isNotEmpty()) {
            formatted.append("  ${"Download Info:".padEnd(labelWidth, space.single())}[ ${downloadInfo.joinToString(" ")} ]\n")
        }

        if (additionalSettings.isNotEmpty()) {
            formatted.append("\n  Additional Settings:\n")
            additionalSettings.forEach { formatted.append("    $it\n") }
        }
        return formatted.toString().trimEnd()  // 移除末尾的换行符
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