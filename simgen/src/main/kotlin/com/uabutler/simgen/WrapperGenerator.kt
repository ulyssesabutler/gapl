package com.uabutler.simgen

import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.uabutler.Analyzer
import com.uabutler.netlistir.netlist.Module
import com.uabutler.netlistir.util.InvocationGraph
import com.uabutler.simengine.Engine
import com.uabutler.simengine.PortValue
import com.uabutler.simgen.runtime.InterfaceValidator
import com.uabutler.simgen.runtime.PortDescriptor
import com.uabutler.simtrace.VcdTracer
import com.uabutler.util.StandardLibraryFunctions
import com.uabutler.vcd.VcdWriter
import java.io.File
import java.io.Writer

/**
 * Generates a thin, named-port Kotlin wrapper class for a compiled GAPL design — a real Kotlin
 * property per port instead of Engine's stringly-typed writeInputPort/readOutputPort. A flat
 * wire[N] port is a plain List<Boolean> property (bit 0 = LSB, matching Engine's own
 * representation); a Record- or Vector-shaped port gets a generated nested data class mirroring the
 * GAPL interface's own structure, converting to/from Engine's generic PortValue tree under the hood.
 * Every port property delegates to a live Engine instance built by recompiling the given GAPL source
 * fresh at construction time.
 */
object WrapperGenerator {
    const val DEFAULT_PACKAGE_NAME = "com.uabutler.simgen.generated"

    private val listOfBoolean = LIST.parameterizedBy(BOOLEAN)
    private val portValueClassName = PortValue::class.asClassName()
    private val portValueBits = portValueClassName.nestedClass("Bits")
    private val portValueFields = portValueClassName.nestedClass("Fields")
    private val portValueElements = portValueClassName.nestedClass("Elements")

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

    /** Everything needed to use one shape (as a port, or as a field within a generated record):
     *  the Kotlin type to declare it as, any nested record class(es) it introduces (to be added to
     *  the immediately-enclosing class), an all-false default value, and how to convert a value of
     *  this shape to/from PortValue. */
    private class ResolvedShape(
        val typeName: TypeName,
        val nestedTypes: List<TypeSpec>,
        val defaultValue: CodeBlock,
        val toPortValue: (CodeBlock) -> CodeBlock,
        val fromPortValue: (CodeBlock) -> CodeBlock,
    )

    private fun pascalCase(name: String): String =
        name.split("_").joinToString("") { it.replaceFirstChar(Char::uppercase) }

    private fun resolveShape(shape: PortShape, fieldName: String, enclosing: ClassName): ResolvedShape = when (shape) {
        is PortShape.Leaf -> ResolvedShape(
            typeName = listOfBoolean,
            nestedTypes = emptyList(),
            defaultValue = CodeBlock.of("List(%L) { false }", shape.width),
            toPortValue = { expr -> CodeBlock.of("%T(%L)", portValueBits, expr) },
            fromPortValue = { expr -> CodeBlock.of("(%L as %T).bits", expr, portValueBits) },
        )

        is PortShape.Vector -> {
            val element = resolveShape(shape.element, fieldName, enclosing)
            ResolvedShape(
                typeName = LIST.parameterizedBy(element.typeName),
                nestedTypes = element.nestedTypes,
                defaultValue = CodeBlock.of("List(%L) { %L }", shape.size, element.defaultValue),
                toPortValue = { expr ->
                    CodeBlock.of("%T(%L.map { %L })", portValueElements, expr, element.toPortValue(CodeBlock.of("it")))
                },
                fromPortValue = { expr ->
                    CodeBlock.of("(%L as %T).elements.map { %L }", expr, portValueElements, element.fromPortValue(CodeBlock.of("it")))
                },
            )
        }

        is PortShape.Record -> {
            val recordClassName = enclosing.nestedClass(pascalCase(fieldName))
            val fieldResolutions = shape.fields.mapValues { (key, sub) -> resolveShape(sub, key, recordClassName) }
            val typeSpec = buildRecordTypeSpec(recordClassName, shape, fieldResolutions)
            val defaultArgs = fieldResolutions.entries.map { (k, r) -> CodeBlock.of("%N = %L", k, r.defaultValue) }
            ResolvedShape(
                typeName = recordClassName,
                nestedTypes = listOf(typeSpec),
                defaultValue = CodeBlock.of("%T(${joinPlaceholders(defaultArgs.size)})", recordClassName, *defaultArgs.toTypedArray()),
                toPortValue = { expr -> CodeBlock.of("%L.toPortValue()", expr) },
                fromPortValue = { expr -> CodeBlock.of("%T.fromPortValue(%L)", recordClassName, expr) },
            )
        }
    }

    private fun buildRecordTypeSpec(className: ClassName, shape: PortShape.Record, fieldResolutions: Map<String, ResolvedShape>): TypeSpec {
        val nestedTypeNames = fieldResolutions.values.flatMap { it.nestedTypes }.map { it.name }
        require(nestedTypeNames.size == nestedTypeNames.distinct().size) {
            "Generated nested class name collision among fields of '${className.simpleName}': $nestedTypeNames — " +
                "two field keys produced the same PascalCase name"
        }

        val constructor = FunSpec.constructorBuilder()
        val properties = mutableListOf<PropertySpec>()
        val toPortValueEntries = mutableListOf<CodeBlock>()
        val fromPortValueArgs = mutableListOf<CodeBlock>()

        fieldResolutions.forEach { (fieldName, resolved) ->
            constructor.addParameter(fieldName, resolved.typeName)
            properties += PropertySpec.builder(fieldName, resolved.typeName).initializer(fieldName).build()
            toPortValueEntries += CodeBlock.of("%S to %L", fieldName, resolved.toPortValue(CodeBlock.of(fieldName)))
            fromPortValueArgs += CodeBlock.of(
                "%N = %L", fieldName, resolved.fromPortValue(CodeBlock.of("fields.getValue(%S)", fieldName))
            )
        }

        val toPortValueFun = FunSpec.builder("toPortValue")
            .returns(portValueClassName)
            .addStatement("return %T(mapOf(${joinPlaceholders(toPortValueEntries.size)}))", portValueFields, *toPortValueEntries.toTypedArray())
            .build()

        val fromPortValueFun = FunSpec.builder("fromPortValue")
            .addParameter("value", portValueClassName)
            .returns(className)
            .addStatement("val fields = (value as %T).fields", portValueFields)
            .addStatement("return %T(${joinPlaceholders(fromPortValueArgs.size)})", className, *fromPortValueArgs.toTypedArray())
            .build()

        val companion = TypeSpec.companionObjectBuilder()
            .addFunction(fromPortValueFun)
            .build()

        val typeSpecBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.DATA)
            .primaryConstructor(constructor.build())
            .addProperties(properties)
            .addFunction(toPortValueFun)
            .addType(companion)

        fieldResolutions.values.flatMap { it.nestedTypes }.forEach { typeSpecBuilder.addType(it) }

        return typeSpecBuilder.build()
    }

    private fun joinPlaceholders(count: Int) = List(count) { "%L" }.joinToString(", ")

    private fun buildFileSpec(packageName: String, className: String, module: Module): FileSpec {
        val gaplFunctionName = module.invocation.gaplFunctionName
        val inputPorts = PortInspector.inputPorts(module)
        val outputPorts = PortInspector.outputPorts(module)
        val simulatorClassName = ClassName(packageName, className)

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

        val nestedTypes = mutableListOf<TypeSpec>()

        val inputProperties = inputPorts.map { port ->
            val resolved = resolveShape(port.shape, port.name, simulatorClassName)
            nestedTypes += resolved.nestedTypes
            val isLeaf = port.shape is PortShape.Leaf
            PropertySpec.builder(port.name, resolved.typeName)
                .mutable(true)
                .initializer(resolved.defaultValue)
                .setter(
                    FunSpec.setterBuilder()
                        .addParameter("value", resolved.typeName)
                        .addStatement("field = value")
                        .apply {
                            if (isLeaf) {
                                addStatement("engine.writeInputPort(%S, value)", port.name)
                            } else {
                                addStatement("engine.writeInputPort(%S, %L)", port.name, resolved.toPortValue(CodeBlock.of("value")))
                            }
                        }
                        .build()
                )
                .build()
        }

        val outputProperties = outputPorts.map { port ->
            val resolved = resolveShape(port.shape, port.name, simulatorClassName)
            nestedTypes += resolved.nestedTypes
            val isLeaf = port.shape is PortShape.Leaf
            PropertySpec.builder(port.name, resolved.typeName)
                .getter(
                    FunSpec.getterBuilder()
                        .apply {
                            if (isLeaf) {
                                addStatement("return engine.readOutputPort(%S)", port.name)
                            } else {
                                addStatement(
                                    "return %L",
                                    resolved.fromPortValue(CodeBlock.of("engine.readOutputPortValue(%S)", port.name)),
                                )
                            }
                        }
                        .build()
                )
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

        val typeSpecBuilder = TypeSpec.classBuilder(className)
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

        nestedTypes.forEach { typeSpecBuilder.addType(it) }

        return FileSpec.builder(packageName, className).addType(typeSpecBuilder.build()).build()
    }

    private fun portDescriptorListCode(ports: List<Port>): CodeBlock {
        val builder = CodeBlock.builder().add("listOf(")
        ports.forEachIndexed { index, port ->
            if (index > 0) builder.add(", ")
            builder.add("%T(%S, %L)", PortDescriptor::class, port.name, portShapeCode(port.shape))
        }
        builder.add(")")
        return builder.build()
    }

    private fun portShapeCode(shape: PortShape): CodeBlock = when (shape) {
        is PortShape.Leaf -> CodeBlock.of("%T(%L)", PortShape.Leaf::class, shape.width)
        is PortShape.Vector -> CodeBlock.of("%T(%L, %L)", PortShape.Vector::class, portShapeCode(shape.element), shape.size)
        is PortShape.Record -> {
            val entries = shape.fields.entries.map { (k, v) -> CodeBlock.of("%S to %L", k, portShapeCode(v)) }
            CodeBlock.of("%T(mapOf(${joinPlaceholders(entries.size)}))", PortShape.Record::class, *entries.toTypedArray())
        }
    }
}
