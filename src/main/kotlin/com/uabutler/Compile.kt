package com.uabutler

import com.uabutler.gaplir.builder.ModuleBuilder
import com.uabutler.verilogir.builder.VerilogBuilder
import kotlin.system.exitProcess

fun compile(args: Array<String>) {
    if (args.size != 1) {
        println("Usage: gapl [filename]")
        println("Received: [${args.joinToString(",")}]")
        exitProcess(1)
    }

    val fileName = args[0]
    println("Compiling file: $fileName")
}

fun test() {
    val programSimplePassthrough = """
        function passthrough() i: wire[32] => o: wire[32]
        {
            i => o;
        }
    """.trimIndent()

    val programSimplePassthrough2 = """
        function passthrough() i: wire[32] => o: wire[32]
        {
            i => declare t1: wire[32] => declare t2: wire[32] => declare t: wire[32];
            t => o;
        }
    """.trimIndent()

    val programDoublePassthrough = """
        function passthrough() i1: wire[32], i2: wire[32] => o1: wire[32], o2: wire[32]
        {
            i1, i2 => o1, o2;
        }
    """.trimIndent()

    val interfaces = """
        interface payload()
        {
            data: wire[8];
            metadata: wire[8];
        }
        
        function test() i: payload() => o: payload()
        {
            i => o;
        }
    """.trimIndent()

    val function = """
        function test1() i: wire[8] => o: wire[8]
        {
            i => o;
        }
        
        function test2() i: wire[8] => o: wire[8]
        {
            i => function test1() => o;
        }
    """.trimIndent()

    val interfaceMemberAccess = """
        interface payload()
        {
            data: wire[8];
            metadata: wire[8];
        }
        
        function test() i: payload() => o: wire[8]
        {
            i.data => o;
        }
    """.trimIndent()

    val interfaceMemberAccesses = """
        interface data()
        {
            first: wire[8];
            second: wire[8];
        }
        
        interface payload()
        {
            data: data();
            metadata: wire[8];
        }
        
        function test1() i: payload() => o: wire[8]
        {
            i.data.first => o;
        }
        
        function test2() i: payload() => o: wire[8]
        {
            i.data.second => o;
        }
    """.trimIndent()

    val interfaceVectorAccess = """
        function test() i: wire[8][6][4][2] => o: wire[8]
        {
            i[1][3][5] => o;
        }
    """.trimIndent()


    val programInterfaces = """
        interface sub_payload()
        {
            first: wire[8];
            second: wire[8];
        }
        
        interface payload()
        {
            a: sub_payload();
            b: sub_payload();
        }
        
        interface data_stream()
        {
            data: payload()[8];
            metadata: wire[16];
        }
        
        function sub() i: data_stream() => o: data_stream()
        {
            i => o;
        }
        
        function test() i: data_stream() => o: data_stream() 
        {
            t: data_stream();
            i => sub() => t;
            t => o;
        }
    """.trimIndent()

    val programMultipleInterfaces = """
        interface payload()
        {
            data: wire[32];
            metadata: wire[16];
        }
        
        function sub() s1: payload(), s2: payload() => so: payload()
        {
            s1 => so;
        }
        
        function test() t1: payload(), t2: payload() => to: payload() 
        {
            t1, t2 => sub() => to;
        }
    """.trimIndent()

    val programMyAdd = """
        function my_add() a1: wire[32], a2: wire[32] => o1: wire[32]
        {
            a1 => o1;
        }
        
        function add_test() i1: wire[32], i2: wire[32] => o: wire[32]
        {
            i1, i2 => my_add() => o;
        }
    """.trimIndent()

    val programAddNamed = """
        function add_test() i1: wire[32], i2: wire[32] => o: wire[32]
        {
            i1, i2 => addition: add() => o;
        }
    """.trimIndent()

    val programAddAnon = """
        function add_test() i1: wire[32], i2: wire[32] => o: wire[32]
        {
            i1, i2 => function add() => o;
        }
    """.trimIndent()

    val test = """
        interface payload()
        {
            data: wire[32][8];
            metadata: wire[32];
        }
        
        function combine() i: payload() => o: wire[32]
        {
            i.data[1], i.metadata => function right_shift() => declare t: wire[32];
            
            t => function register() => o;
        }
    """.trimIndent()

    val programRegister = """
        function test_register() i: wire[32] => o: wire[32]
        {
            i => function register() => o;
        }
    """.trimIndent()

    val parser = Parser.fromString(test)
    val ast = parser.program()
    val gaplirBuilder = ModuleBuilder(ast)
    val gaplirModules = gaplirBuilder.buildAllModules()
    val verilogirModules = gaplirModules.map { VerilogBuilder.verilogModuleFromGAPLModule(it) }
    verilogirModules.forEach { println(it.verilogSerialize()) }
}

fun main(args: Array<String>) = test()