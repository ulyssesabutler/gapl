import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.*
import org.gradle.process.ExecSpec

abstract class VivadoTask : DefaultTask() {

    @get:Input
    abstract val vivadoCommand: ListProperty<String>

    @TaskAction
    fun runVivadoCommand() {
        val command = vivadoCommand.get().joinToString(" ")
        val vivadoSettings = project.findProperty("vivadoSettings")?.toString() ?: throw IllegalStateException("Missing 'vivadoSettings' property in gradle.properties")

        project.exec {
            it.executable = "bash"
            it.args = listOf("-c", "source $vivadoSettings && $command")
        }
    }
}