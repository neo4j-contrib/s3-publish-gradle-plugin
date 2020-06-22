package com.neo4j.gradle.s3

import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.auth.SystemPropertiesCredentialsProvider
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.PutObjectResult
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.logging.Logger
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.property
import java.io.File


open class S3Extension(objects: ObjectFactory) {
  val profile: Property<String> = objects.property()
  val region: Property<String> = objects.property()
  val bucket: Property<String> = objects.property()
}

open class S3Plugin : Plugin<Project> {

  override fun apply(target: Project) {
    target.extensions.create("s3", S3Extension::class.java)
  }
}

class S3Client(profileCreds: ProfileCredentialsProvider, regionValue: String) {
  private val underlying: AmazonS3

  init {
    val creds = AWSCredentialsProviderChain(
      EnvironmentVariableCredentialsProvider(),
      SystemPropertiesCredentialsProvider(),
      profileCreds,
      EC2ContainerCredentialsProviderWrapper()
    )
    val amazonS3ClientBuilder = AmazonS3ClientBuilder
      .standard()
      .withCredentials(creds)
    if (regionValue.isNotBlank()) {
      amazonS3ClientBuilder.withRegion(regionValue)
    }
    underlying = amazonS3ClientBuilder.build()
  }

  fun doesObjectExist(bucketValue: String, destinationPath: String) = underlying.doesObjectExist(bucketValue, destinationPath)

  fun putObject(putObjectRequest: PutObjectRequest): PutObjectResult = underlying.putObject(putObjectRequest)
}

data class S3UploadContext(val destination: String, val bucket: String, val overwrite: Boolean = false, val acl: CannedAccessControlList? = null)

class S3UploadProcessor(private val s3Client: S3Client, private val s3UploadContext: S3UploadContext, private val logger: Logger) {

  fun process(files: Set<File>, baseDir: File) {
    val (destination, bucket, overwrite, acl) = s3UploadContext
    val bucketValue = bucket.removeSuffix("/")
    files.forEach { file ->
      val relativePath = if (baseDir.isDirectory) {
        file.relativeTo(baseDir)
      } else {
        file.relativeTo(baseDir.parentFile)
      }
      val destinationPath = if (destination != "") {
        "${destination.removeSuffix("/")}/${relativePath.invariantSeparatorsPath.removePrefix("/")}"
      } else {
        relativePath.invariantSeparatorsPath.removePrefix("/")
      }
      val basePutObjectRequest = PutObjectRequest(bucketValue, destinationPath, file)
      val putObjectRequest = if (acl != null) {
        basePutObjectRequest.withCannedAcl(acl)
      } else {
        basePutObjectRequest
      }
      if (s3Client.doesObjectExist(bucketValue, destinationPath)) {
        if (overwrite) {
          logger.quiet("S3 Uploading $file → s3://$bucketValue/${destinationPath} with overwrite")
          s3Client.putObject(putObjectRequest)
        } else {
          logger.quiet("s3://$bucketValue/${destinationPath} exists, not overwriting")
        }
      } else {
        logger.quiet("S3 Uploading $file → s3://$bucketValue/${destinationPath}")
        s3Client.putObject(putObjectRequest)
      }
    }
  }
}

abstract class S3UploadTask : DefaultTask() {
  @InputFiles
  var sources: MutableList<ConfigurableFileTree> = mutableListOf()

  @Input
  var destination: String = ""

  @Input
  var overwrite: Boolean = false

  @Input
  var bucket: String = ""

  @Input
  var profile: String = ""

  @Input
  var region: String = ""

  @Input
  @Optional
  val acl: Property<CannedAccessControlList> = project.objects.property()

  @TaskAction
  fun task() {
    val s3Extension = project.extensions.findByType(S3Extension::class.java)
    val bucketValue = s3Extension?.bucket?.getOrElse(bucket) ?: bucket
    val regionValue = s3Extension?.region?.getOrElse(region) ?: region
    val profileValue = s3Extension?.profile?.getOrElse(profile) ?: profile
    val profileCreds = if (profileValue.isNotBlank()) {
      logger.quiet("Using AWS credentials profile: $profileValue")
      ProfileCredentialsProvider(profileValue)
    } else {
      ProfileCredentialsProvider()
    }
    val s3Client = S3Client(profileCreds, regionValue)
    val s3UploadContext = S3UploadContext(destination, bucketValue, overwrite, if(acl.isPresent) acl.get() else null)
    val s3UploadProcessor = S3UploadProcessor(s3Client, s3UploadContext, logger)
    sources.forEach { source ->
      s3UploadProcessor.process(source.files, source.dir)
    }
  }

  fun setSource(source: String) {
    this.sources.add(project.fileTree(source))
  }

  fun setSource(vararg sources: String?) {
    sources.forEach {
      if (it != null) {
        this.sources.add(project.fileTree(it))
      }
    }
  }

  fun setSource(sources: List<String>) {
    sources.forEach {
      this.sources.add(project.fileTree(it))
    }
  }

  fun setSource(source: ConfigurableFileTree) {
    this.sources.add(source)
  }
}
