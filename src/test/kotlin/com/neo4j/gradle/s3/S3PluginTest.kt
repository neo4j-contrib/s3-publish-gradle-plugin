package com.neo4j.gradle.s3

import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

  @Test
  fun `should create a content type mapping`() {
    val s3Client = mockk<S3Client>(relaxed = true)
    val project = ProjectBuilder.builder().build()
    val contentTypeMapping = mapOf(
      ".mpeg" to "video/mpeg",
      ".json" to "text/x-json"
    )
    val s3Plugin = S3UploadProcessor(s3Client, S3UploadContext("data", "foo", contentTypeMapping = contentTypeMapping), project.logger)

    assertTrue(s3Plugin.contentTypeMapping[".json"] == "text/x-json")
    assertTrue(s3Plugin.contentTypeMapping[".css"] == "text/css")
  }

  @Test
  fun `should assign content-type on object metadata (when exists)`() {
    // when
    val resourcesFile = File(S3PluginTest::class.java.getResource("/files").toURI())
    val calendarFile = File(S3PluginTest::class.java.getResource("/files/calendar.ics").toURI())
    val jsonFile = File(S3PluginTest::class.java.getResource("/files/data.json").toURI())
    val svgFile = File(S3PluginTest::class.java.getResource("/files/logo.svg").toURI())
    val cssFile = File(S3PluginTest::class.java.getResource("/files/style.css").toURI())
    val txtFile = File(S3PluginTest::class.java.getResource("/files/test.txt").toURI())
    val mpegFile = File(S3PluginTest::class.java.getResource("/files/video.mpeg").toURI())

    val s3Client = mockk<S3Client>(relaxed = true)
    every { s3Client.doesObjectExist("guides.neo4j.com/intro", any()) } returns false
    val project = ProjectBuilder.builder().build()
    val contentTypeMapping = mapOf(
      ".mpeg" to "video/mpeg",
      ".json" to "text/x-json"
    )
    val s3Plugin = S3UploadProcessor(s3Client, S3UploadContext("", "guides.neo4j.com/intro/", contentTypeMapping = contentTypeMapping), project.logger)

    // then
    s3Plugin.process(setOf(
      calendarFile,
      jsonFile,
      svgFile,
      cssFile,
      txtFile,
      mpegFile
    ), resourcesFile)

    verifyUploadObject(s3Client = s3Client, bucketName = "guides.neo4j.com/intro", destinationPath = "calendar.ics", contentType = null)
    verifyUploadObject(s3Client = s3Client, bucketName = "guides.neo4j.com/intro", destinationPath = "data.json", contentType = "text/x-json")
    verifyUploadObject(s3Client = s3Client, bucketName = "guides.neo4j.com/intro", destinationPath = "logo.svg", contentType = "image/svg+xml")
    verifyUploadObject(s3Client = s3Client, bucketName = "guides.neo4j.com/intro", destinationPath = "style.css", contentType = "text/css")
    verifyUploadObject(s3Client = s3Client, bucketName = "guides.neo4j.com/intro", destinationPath = "test.txt", contentType = "text/plain")
    verifyUploadObject(s3Client = s3Client, bucketName = "guides.neo4j.com/intro", destinationPath = "video.mpeg", contentType = "video/mpeg")
    confirmVerified(s3Client)
  }

  private fun verifyUploadObject(s3Client: S3Client, bucketName: String, destinationPath: String, contentType: String?) {
    verify { s3Client.doesObjectExist(bucketName, destinationPath) }
    verify { s3Client.putObject(match {
      it.bucketName == bucketName && it.key == destinationPath && ((contentType == null && it.metadata == null) || (contentType != null && it.metadata.contentType == contentType))
    }) }
  }
}
