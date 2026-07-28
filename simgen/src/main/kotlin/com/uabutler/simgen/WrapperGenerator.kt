package com.uabutler.simgen

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.uabutler.Analyzer
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.util.InvocationGraph
import com.uabutler.simengine.Engine
import com.uabutler.simgen.runtime.InterfaceValidator
import com.uabutler.simgen.runtime.PortDescriptor
import com.uabutler.simtrace.VcdTracer
import com.uabutler.util.StandardLibraryFunctions
import com.uabutler.vcd.VcdWriter
import java.io.File
import java.io.Writer

/**
 * Generates a thin, named-port Kotlin wrapper class for a compiled GAPL design — a real Kotlin
 * property per port (typed List<Boolean>, bit 0 = LSB, matching Engine's own representation)
 * instead of Engine's stringly-typed writeInputPort/readOutputPort. Every port property delegates
 * to a live Engine instance built by recompiling the given GAPL source fresh at construction time.
 */
object WrapperGenerator {
    const val DEFAULT_PACKAGE_NAME = "com.uabutler.simgen.generated"

    fun generate(
        gaplSource: String,
        targetModuleName: String? = null,
        packageName: String = DEFAULT_PACKAGE_NAME,
        className: String? = null,
    ): FileSpec {
        val analysis = Analyzer.analyzeFull(gaplSource)
        if (analysis.modules == null) {
            error("Failed to compile GAPL source:\n" + analysis.diagnostics.joinToString("\n"))
        }
        val modules = analysis.modules!!
        // Stdlib helpers that happen not to be called by anything else are themselves root modules
        // by InvocationGraph's definition (no incoming invocation edges) — exclude them so root
        // selection reflects the user's own top-level design, not incidental unreferenced stdlib
        // functions (e.g. with the default includeStdLib = true, a single-function user design would
        // otherwise appear to have several "root modules" purely from unused stdlib helpers).
        val stdlibNames = StandardLibraryFunctions.entries.map { it.identifier }.toSet()
        val candidateRoots = InvocationGraph(modules).rootModules()
            .filterNot { it.invocation.gaplFunctionName in stdlibNames }
        val target = RootModuleResolver.resolve(candidateRoots, targetModuleName)
        val resolvedClassName = className
            ?: target.invocation.gaplFunctionName.split("_")
                .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } } + "Simulator"
        return buildFileSpec(packageName, resolvedClassName, target)
    }

    private fun buildFileSpec(packageName: String, className: String, module: Module): FileSpec {
        val gaplFunctionName = module.invocation.gaplFunctionName
        val inputPorts = PortInspector.inputPorts(module)
        val outputPorts = PortInspector.outputPorts(module)
        val listOfBoolean = LIST.parameterizedBy(BOOLEAN)

        val nullableFile = File::class.asClassName().copy(nullable = true)
        val nullableTracer = VcdTracer::class.asClassName().copy(nullable = true)
        val nullableWriter = Writer::class.asClassName().copy(nullable = true)

        val vcdOutputParam = ParameterSpec.builder("vcdOutput", nullableFile)
            .defaultValue("null")
            .build()

        val constructor = FunSpec.constructorBuilder()
            .addParameter("gaplSource", String::class)
            .addParameter(vcdOutputParam)
            .build()

        val engineProperty = PropertySpec.builder("engine", Engine::class)
            .addModifiers(KModifier.PRIVATE)
            .build()
        val tracerProperty = PropertySpec.builder("tracer", nullableTracer)
            .addModifiers(KModifier.PRIVATE)
            .build()
        val vcdWriterSinkProperty = PropertySpec.builder("vcdWriterSink", nullableWriter)
            .addModifiers(KModifier.PRIVATE)
            .build()

        val initBlock = CodeBlock.builder()
            .addStatement("val analysis = %T.analyzeFull(gaplSource)", Analyzer::class)
            .add(
                "val module = %T.validate(\n",
                InterfaceValidator::class,
            )
            .indent()
            .addStatement("gaplFunctionName = %S,", gaplFunctionName)
            .addStatement("modules = analysis.modules,")
            .addStatement("diagnostics = analysis.diagnostics,")
            .addStatement("expectedInputs = %L,", portDescriptorListCode(inputPorts))
            .addStatement("expectedOutputs = %L,", portDescriptorListCode(outputPorts))
            .unindent()
            .addStatement(")")
            .addStatement("engine = %T.build(analysis.modules!!, module.invocation)", Engine::class)
            .beginControlFlow("if (vcdOutput != null)")
            .addStatement("val sink = vcdOutput.bufferedWriter()")
            .addStatement("vcdWriterSink = sink")
            .addStatement("tracer = %T(engine, %T(sink)).also { it.dumpInitial() }", VcdTracer::class, VcdWriter::class)
            .nextControlFlow("else")
            .addStatement("vcdWriterSink = null")
            .addStatement("tracer = null")
            .endControlFlow()
            .build()

        val inputProperties = inputPorts.map { port ->
            PropertySpec.builder(port.name, listOfBoolean)
                .mutable(true)
                .initializer("List(%L) { false }", port.width)
                .setter(
                    FunSpec.setterBuilder()
                        .addParameter("value", listOfBoolean)
                        .addStatement("field = value")
                        .addStatement("engine.writeInputPort(%S, value)", port.name)
                        .build()
                )
                .build()
        }

        val outputProperties = outputPorts.map { port ->
            PropertySpec.builder(port.name, listOfBoolean)
                .getter(FunSpec.getterBuilder().addStatement("return engine.readOutputPort(%S)", port.name).build())
                .build()
        }

        val settleFun = FunSpec.builder("settle").addStatement("engine.settle()").build()

        val tickFun = FunSpec.builder("tick")
            .addStatement("val activeTracer = tracer")
            .beginControlFlow("if (activeTracer != null)")
            .addStatement("activeTracer.tick()")
            .nextControlFlow("else")
            .addStatement("engine.tick()")
            .endControlFlow()
            .build()

        val closeFun = FunSpec.builder("close")
            .addModifiers(KModifier.OVERRIDE)
            .addStatement("vcdWriterSink?.close()")
            .build()

        val typeSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(AutoCloseable::class)
            .primaryConstructor(constructor)
            .addProperty(engineProperty)
            .addProperty(tracerProperty)
            .addProperty(vcdWriterSinkProperty)
            .addInitializerBlock(initBlock)
            .addProperties(inputProperties)
            .addProperties(outputProperties)
            .addFunction(settleFun)
            .addFunction(tickFun)
            .addFunction(closeFun)
            .build()

        return FileSpec.builder(packageName, className).addType(typeSpec).build()
    }

    private fun portDescriptorListCode(ports: List<FlatPort>): CodeBlock {
        val builder = CodeBlock.builder().add("listOf(")
        ports.forEachIndexed { index, port ->
            if (index > 0) builder.add(", ")
            builder.add("%T(%S, %L)", PortDescriptor::class, port.name, port.width)
        }
        builder.add(")")
        return builder.build()
    }
}
