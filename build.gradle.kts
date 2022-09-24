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

fun Task.pushToGithub(message: String = "Pushing from Gradle") {
    exec {
        executable = "git"
        args("add", ".")
        workingDir = project.rootDir
    }
    exec {
        executable = "git"
        args("commit", "-m", message)
        workingDir = project.rootDir
        isIgnoreExitValue = true
    }
    exec {
        executable = "git"
        args("push")
        workingDir = project.rootDir
    }
}

val pushToDevAndTestAsDependency = tasks.register("test-dep") {
    pushToGithub("Pushing from Gradle to test as dependency")
    exec {
        executable = "python3"
        args("test_as_module_gh.py", "dev")
        workingDir = project.rootDir
    }
}

val pushToGithubStaging = tasks.register("stage") {

    dependsOn(pushToDevAndTestAsDependency)
    dependsOn(fullTest)

    fun increaseBuildNum(filename: String): Int =
        project.rootDir.resolve(filename).let {
            val buildNum = it.readText().toInt() + 1
            it.writeText(buildNum.toString())
            buildNum
        }

    doLast {
        val buildNum = increaseBuildNum(".github/staging_build_num.txt")
        pushToGithub("Pushing from Gradle with build num $buildNum")
        println("Pushed to Git with build num $buildNum")
    }
}

tasks.register("updateReadmeVersion") {
    doFirst {
        // найдем что-то вроде "io.github.rtmigo:lib:0.0.1"
        // и поменяем на актуальную версию

        fun File.replaceInText(rx: Regex, replacement: String) {
            val old = this.readText()
            val new = rx.replace(old, replacement)
            if (new != old)
                this.writeText(new)
        }

        val prefixToFind = "io.github.rtmigo:later:"
        project.rootDir.resolve("README.md").replaceInText(
            """(?<=${Regex.escape(prefixToFind)})[0-9\.+]+""".toRegex(),
            project.version.toString()
        )
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
