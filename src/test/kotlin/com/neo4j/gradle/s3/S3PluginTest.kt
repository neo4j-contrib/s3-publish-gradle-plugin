package com.neo4j.gradle.s3

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

class S3PluginTest {
  @Test
  fun `plugin registers task`() {
    // Create a test project and apply the plugin
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("com.neo4j.gradle.s3.S3Plugin")
    // Verify the result
    assertNotNull(project.extensions.findByName("s3"))
  }

  @Test
  fun `should remove leading slash`() {
    // when
    val resourcesFile = File(S3PluginTest::class.java.getResource("/files").toURI())
    val txtFile = File(S3PluginTest::class.java.getResource("/files/test.txt").toURI())
    val s3Client = mockk<S3Client>(relaxed = true)
    every { s3Client.doesObjectExist("foo", "test.txt") } returns false
    val project = ProjectBuilder.builder().build()
    val s3Plugin = S3UploadProcessor(s3Client, S3UploadContext("", "foo"), project.logger)

    // then
    s3Plugin.process(setOf(txtFile), resourcesFile)

    verify { s3Client.doesObjectExist("foo", "test.txt") }
    verify { s3Client.putObject(match { it.bucketName == "foo" && it.key == "test.txt" }) }
    confirmVerified(s3Client)
  }

  @Test
  fun `should remove leading slash when destination contains leading slash`() {
    // when
    val resourcesFile = File(S3PluginTest::class.java.getResource("/files").toURI())
    val txtFile = File(S3PluginTest::class.java.getResource("/files/test.txt").toURI())
    val s3Client = mockk<S3Client>(relaxed = true)
    every { s3Client.doesObjectExist("foo", "test.txt") } returns false
    val project = ProjectBuilder.builder().build()
    val s3Plugin = S3UploadProcessor(s3Client, S3UploadContext("/bar", "foo"), project.logger)

    // then
    s3Plugin.process(setOf(txtFile), resourcesFile)

    verify { s3Client.doesObjectExist("foo", "/bar/test.txt") }
    verify { s3Client.putObject(match { it.bucketName == "foo" && it.key == "/bar/test.txt" }) }
    confirmVerified(s3Client)
  }

  @Test
  fun `should remove leading slash when bucket and destination contain leading slash`() {
    // when
    val resourcesFile = File(S3PluginTest::class.java.getResource("/files").toURI())
    val txtFile = File(S3PluginTest::class.java.getResource("/files/test.txt").toURI())
    val s3Client = mockk<S3Client>(relaxed = true)
    every { s3Client.doesObjectExist("guides.neo4j.com/intro", "/txt/test.txt") } returns false
    val project = ProjectBuilder.builder().build()
    val s3Plugin = S3UploadProcessor(s3Client, S3UploadContext("/txt", "guides.neo4j.com/intro/"), project.logger)

    // then
    s3Plugin.process(setOf(txtFile), resourcesFile)

    verify { s3Client.doesObjectExist("guides.neo4j.com/intro", "/txt/test.txt") }
    verify { s3Client.putObject(match { it.bucketName == "guides.neo4j.com/intro" && it.key == "/txt/test.txt" }) }
    confirmVerified(s3Client)
  }
}
