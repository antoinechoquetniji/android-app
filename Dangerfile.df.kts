@file:Repository("https://repo.maven.apache.org")
@file:DependsOn("org.apache.commons:commons-text:1.6")
@file:DependsOn("com.gianluz:danger-kotlin-android-lint-plugin:0.1.0")
@file:DependsOn("io.github.ackeecz:danger-kotlin-detekt:0.1.4")

import io.github.ackeecz.danger.detekt.DetektPlugin
import com.gianluz.dangerkotlin.androidlint.*
import systems.danger.kotlin.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.function.BiPredicate
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

}

val detektReports = Files.find(Paths.get(""), 10, BiPredicate { path, attributes ->
    val fileName = path.toFile().name
    fileName.endsWith("detekt.xml")
}).map { it.toFile() }.collect(Collectors.toList())

DetektPlugin.parseAndReport(*detektReports.toTypedArray())

androidLint {
    // Fail for each Fatal in a single module
    val moduleLintFilePaths = find(
            "app/build/reports/",
            "lint-results.xml",
            "lint-results-debug.xml",
            "lint-results-release.xml"
    ).toTypedArray()

    parseAllDistinct(*moduleLintFilePaths).forEach {
        if(it.severity == "Fatal" || it.severity == "Error")
            fail(
                    "Danger lint check failed: ${it.message}",
                    it.location.file.replace(System.getProperty("user.dir"), ""),
                    Integer.parseInt(it.location.line)
            )
        if(it.severity == "Warning")
            warn(
                    "Danger lint check failed: ${it.message}",
                    it.location.file.replace(System.getProperty("user.dir"), ""),
                    Integer.parseInt(it.location.line)
            )
    }
}
