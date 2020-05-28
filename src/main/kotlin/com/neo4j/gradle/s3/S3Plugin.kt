package com.neo4j.gradle.s3

import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.auth.SystemPropertiesCredentialsProvider
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.PutObjectRequest
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.property


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
    val s3Client = amazonS3ClientBuilder.build()
    sources.forEach { source ->
      source.forEach { file ->
        val relativePath = if (source.dir.isDirectory) {
          file.relativeTo(source.dir)
        } else {
          file.relativeTo(source.dir.parentFile)
        }
        val destinationPath = "${destination}/${relativePath}"
        val basePutObjectRequest = PutObjectRequest(bucketValue, destinationPath, file)
        val putObjectRequest = if (acl.isPresent) {
          basePutObjectRequest.withCannedAcl(acl.get())
        } else {
          basePutObjectRequest
        }
        if (s3Client.doesObjectExist(bucketValue, destinationPath)) {
          if (overwrite) {
            logger.quiet("S3 Uploading $file → s3://${bucketValue}/${destinationPath} with overwrite")
            s3Client.putObject(putObjectRequest)
          } else {
            logger.quiet("s3://${bucketValue}/${destinationPath} exists, not overwriting")
          }
        } else {
          logger.quiet("S3 Uploading $file → s3://${bucketValue}/${destinationPath}")
          s3Client.putObject(putObjectRequest)
        }
      }
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
