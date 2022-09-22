import sys, os
from pathlib import Path

from tempp import *

module="io.github.rtmigo:later"

url="https://github.com/rtmigo/later_kt.git"

code="""
    import io.github.rtmigo.later.*

    fun main() {
        println("Hi!".asLater())
    }
"""

try:
    imp_details = """{ version { branch = "__BRANCH__" } }""".replace("__BRANCH__", sys.argv[1])
except IndexError:
    imp_details = ""

with TempProject(
        files={
            # minimalistic build script to use the library
            "build.gradle.kts": """
                plugins {
                    id("application")
                    kotlin("jvm") version "1.6.20"
                    //java // needed?
                }

                repositories { mavenCentral() }
                application { mainClass.set("MainKt") }

                dependencies {
                    implementation("__MODULE__") __IMP_DETAILS__
                }
            """.replace("__MODULE__", module).replace("__IMP_DETAILS__", imp_details),

            # additional settings, if necessary
            "settings.gradle.kts": """
                sourceControl {
                    gitRepository(java.net.URI("__URL__")) { // # .git
                        producesModule("__MODULE__")
                    }
                }
            """.replace("__MODULE__", module).replace("__URL__", url),

            # kotlin code that imports and uses the library
            "src/main/kotlin/Main.kt": code}) as app:

    app.print_files()
    app.run([os.path.abspath("gradlew"), "clean"]) # , "-q"
    app.run([os.path.abspath("gradlew"), "build"]) # , "-q"
    result = app.run([os.path.abspath("gradlew"), "run"]) # , "-q"

    print("returncode", result.returncode)

    print("stderr", "-"*80)
    print(result.stderr)

    print("stdout", "-"*80)
    print(result.stdout)
    print("-"*80)

    assert result.returncode == 0
    assert result.stdout == "3.0\n", result.stdout

print("Everything is OK!")