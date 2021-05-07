@file:Repository("https://repo.maven.apache.org")
@file:DependsOn("org.apache.commons:commons-text:1.6")
@file:DependsOn("com.gianluz:danger-kotlin-android-lint-plugin:0.1.0")
@file:DependsOn("danger-kotlin-detekt-0.1.4-all.jar")

import com.gianluz.dangerkotlin.androidlint.AndroidLint
import com.gianluz.dangerkotlin.androidlint.androidLint
import io.github.ackeecz.danger.detekt.DetektPlugin
import io.github.ackeecz.danger.detekt.detekt
import systems.danger.kotlin.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors

register plugin DetektPlugin
register plugin AndroidLint

danger(args) {
    val allSourceFiles = git.modifiedFiles + git.createdFiles
    val changelogChanged = allSourceFiles.contains("CHANGELOG.md")
    val sourceChanges = allSourceFiles.firstOrNull { it.contains("src") }

    onGitHub {
        // Changelog
        if (!changelogChanged && sourceChanges != null) {
            warn("Any changes to code should be reflected in the Changelog.")
        }

        // Big PR Check
        if ((pullRequest.additions ?: 0) - (pullRequest.deletions ?: 0) > 300) {
            warn("Big PR, try to keep changes smaller if you can")
        }

        // Work in progress check
        if (pullRequest.title.contains("WIP", false)) {
            warn("PR is classed as Work in Progress")
        }
    }

    androidLint {
        // Fail for each Fatal in a single module
        val moduleLintFilePaths = find(
            "app/build/reports/",
            "lint-results.xml",
            "lint-results-debug.xml",
            "lint-results-release.xml"
        ).toTypedArray()

        parseAllDistinct(*moduleLintFilePaths).forEach {
            val fileRelativePath = it.location.file.replace(
                "${System.getProperty("user.dir")}/",
                ""
            )
            val line = Integer.parseInt(it.location.line)
            if (allSourceFiles.contains(fileRelativePath)) {
                if (it.severity == "Fatal" || it.severity == "Error") {
                    fail("Danger lint check failed: ${it.message}", fileRelativePath, line)
                } else if (it.severity == "Warning") {
                    warn("Danger lint check failed: ${it.message}", fileRelativePath, line)
                }
            }
        }
    }

    detekt {
        val detektReportFiles = Files.find(Paths.get(""), 10, { path, _ ->
            path.toFile().name.endsWith("detekt-report.xml")
        }).map { it.toFile() }.collect(Collectors.toList())
        val detektReports = parseFiles(*detektReportFiles.toTypedArray())
        detektReports.forEach { detektReport ->
            detektReport.files.forEach { file ->
                val realFile = File(file.name)
                val filePath = realFile.absolutePath.removePrefix(
                    "${File("").absolutePath}/"
                )
                if (allSourceFiles.contains(filePath)) {
                    file.errors.forEach { error ->
                        val line = error.line.toIntOrNull() ?: 0
                        val message = "Detekt: ${error.message}, rule: ${error.source}"
                        warn(
                            message = message,
                            file = filePath,
                            line = line
                        )
                    }
                }
            }
        }
    }
}
