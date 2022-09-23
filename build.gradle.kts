plugins {
    kotlin("jvm") //version "1.6.20"
    id("java-library")
    java
}

group = "io.github.rtmigo"
version = "0.0-SNAPSHOT"


tasks.register("pkgver") {
    doLast {
        println(project.version.toString())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")
    testImplementation("io.kotest:kotest-assertions-core:5.4.2")
}

kotlin {
    sourceSets {
        val main by getting
        val test by getting
    }
}

val fullTest = tasks.register<Test>("fullTest") {
    useJUnitPlatform {
        includeTags("slow")
    }
    dependsOn("test")
}

tasks.test {
    useJUnitPlatform {
        excludeTags("slow")
    }
}


//task testFast(type: Test) {
//    useJUnitPlatform {
//        excludeTags 'slow'
//    }
//
//    group 'verification'
//    description 'run unit tests without @Tag("slow") < 2 seconds'
//}
//
//test {
//    useJUnitPlatform {
//        includeTags 'slow'
//    }
//
//    description 'run unit tests tagged with @Tag("slow") > 2 seconds'
//}

val increaseBuildNum = tasks.register("increaseBuildNum") {
    project.rootDir.resolve(".github/staging_build_num.txt").let {
        it.writeText((it.readText().toInt() + 1).toString())
    }
}

val pushToGithub = tasks.register("stage") {
    dependsOn(increaseBuildNum)
    doLast {
        exec {
            executable = "git"
            args("add", ".")
            workingDir = project.rootDir
        }
        exec {
            executable = "git"
            args("commit", "-m", "Pushing from Gradle")
            workingDir = project.rootDir
        }
        exec {
            executable = "git"
            args("push")
            workingDir = project.rootDir
        }
    }
    println("Pushed to Git with increased build num")
}



tasks.register("updateReadmeVersion") {
    doFirst {
        // найдем что-то вроде "io.github.rtmigo:lib:0.0.1"
        // и поменяем на актуальную версию
        val readmeFile = project.rootDir.resolve("README.md")
        val prefixToFind = "io.github.rtmigo:later:"
        val regex = """(?<=${Regex.escape(prefixToFind)})[0-9\.+]+""".toRegex()
        val oldText = readmeFile.readText()
        val newText = regex.replace(oldText, project.version.toString())
        if (newText != oldText)
            readmeFile.writeText(newText)
    }
}

tasks.build {
    dependsOn("updateReadmeVersion")
    //dependsOn(fullTest)
}


tasks.register<Jar>("uberJar") {
    archiveClassifier.set("uber")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
             configurations.runtimeClasspath.get()
                 .filter { it.name.endsWith("jar") }
                 .map { zipTree(it) }
         })
}
