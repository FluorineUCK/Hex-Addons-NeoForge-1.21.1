package util
import org.gradle.api.*
import org.gradle.process.*

class P(val project: Project) {
    fun getStdout(action: ExecSpec.() -> Unit) =
        `java.io`.ByteArrayOutputStream().also {
            project.exec {
                action()
                standardOutput = it
            }
        }.toString().trim()

    val commit_id by lazy {
        getStdout {
            commandLine("env", "jj", "log", "-r", "@", "--template", "commit_id", "--no-graph")
        }
    }

    val change_id by lazy {
        getStdout {
            commandLine("env", "jj", "log", "-r", "@", "--template", "change_id", "--no-graph")
        }
    }

    companion object {
        const val contentRoot = "__CONTENT__"
    }
}