plugins {
    id("com.gradle.plugin-publish") version "0.11.0"
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
    jcenter()
}

dependencies {
    implementation("com.amazonaws:aws-java-sdk-s3:1.11.714")
    implementation(gradleApi())
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

version = "0.1.0"

gradlePlugin {
    plugins {
        create("s3Plugin") {
            id = "com.neo4j.gradle.s3.S3Plugin"
            implementationClass = "com.neo4j.gradle.s3.S3Plugin"
        }
    }
}

pluginBundle {
    website = "https://neo4j.com/"
    vcsUrl = "https://github.com/neo4j-contrib/s3-publish-gradle-plugin"

    (plugins) {
        "s3Plugin" {
            id = "com.neo4j.gradle.s3.S3Plugin"
            displayName = "Publish files to Amazon S3"
            description = "A plugin to publish files to Amazon S3"
            tags = listOf("s3", "publish", "files")
        }
    }
}

// Add a source set for the functional test suite
val functionalTestSourceSet = sourceSets.create("functionalTest") {
}

gradlePlugin.testSourceSets(functionalTestSourceSet)
configurations.getByName("functionalTestImplementation").extendsFrom(configurations.getByName("testImplementation"))

// Add a task to run the functional tests
val functionalTest by tasks.creating(Test::class) {
    testClassesDirs = functionalTestSourceSet.output.classesDirs
    classpath = functionalTestSourceSet.runtimeClasspath
}

val check by tasks.getting(Task::class) {
    // Run the functional tests as part of `check`
    dependsOn(functionalTest)
}
