import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import com.uabutler.parsers.generated.MyLanguageLexer
import com.uabutler.parsers.generated.MyLanguageParser
import com.uabutler.parsers.generated.MyLanguageBaseVisitor

class MyLanguageCustomVisitor: MyLanguageBaseVisitor<Unit>() {
    override fun visitStatement(ctx: MyLanguageParser.StatementContext) {
        val expr = ctx.expression()
        when {
            expr.STRING() != null -> {
                val text = expr.STRING()!!.text.trim('"')
                println(text)
            }
            expr.NUMBER() != null -> {
                val number = expr.NUMBER()!!.text.toInt()
                println(number)
            }
        }
    }

    override fun defaultResult() {
        println("Hello World!")
    }
}

fun main() {
    val input = """
        print "Hello World";
        print 123;
    """.trimIndent()

    val charStream = CharStreams.fromString(input)
    val lexer = MyLanguageLexer(charStream)
    val tokens = CommonTokenStream(lexer)
    val parser = MyLanguageParser(tokens)
    val parseTree = parser.program()
    val visitor = MyLanguageCustomVisitor()
    visitor.visit(parseTree)
}
