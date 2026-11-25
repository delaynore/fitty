package com.delaynore.fitty

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun domainPackageHasNoAndroidComposeUiOrPlatformImports() {
        val domainDir = sourceRoot().resolve("domain")
        val forbidden = listOf("android.", "androidx.", "com.delaynore.fitty.ui.", "com.delaynore.fitty.platform.")
        val violations = findViolations(domainDir, forbidden)
        assertTrue("Forbidden imports in domain/:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun dataPackageHasNoComposeOrUiImports() {
        val dataDir = sourceRoot().resolve("data")
        val forbidden = listOf("androidx.compose.", "com.delaynore.fitty.ui.")
        val violations = findViolations(dataDir, forbidden)
        assertTrue("Forbidden imports in data/:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    private fun sourceRoot(): File =
        listOf(File("src/main/java/com/delaynore/fitty"), File("app/src/main/java/com/delaynore/fitty"))
            .first(File::isDirectory)

    private fun findViolations(root: File, forbiddenPrefixes: List<String>): List<String> =
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                file.readLines()
                    .asSequence()
                    .filter { it.startsWith("import ") }
                    .map { it.removePrefix("import ").trim() }
                    .filter { imported -> forbiddenPrefixes.any(imported::startsWith) }
                    .map { "${file.name}: import $it" }
            }
            .toList()
}
