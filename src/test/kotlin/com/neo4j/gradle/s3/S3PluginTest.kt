package com.neo4j.gradle.s3

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

class S3PluginTest {
    @Test fun `plugin registers task`() {
        // Create a test project and apply the plugin
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.neo4j.gradle.s3.S3Plugin")
        // Verify the result
        assertNotNull(project.extensions.findByName("s3"))
    }
}
