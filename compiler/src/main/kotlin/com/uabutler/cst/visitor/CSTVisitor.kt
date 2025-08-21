package com.uabutler.cst.visitor

import com.uabutler.cst.node.CST
import com.uabutler.cst.node.CSTEmpty
import com.uabutler.parsers.generated.CSTBaseVisitor
import com.uabutler.parsers.generated.CSTLexer
import org.antlr.v4.kotlinruntime.Token

abstract class CSTVisitor: CSTBaseVisitor<CST>() {
    override fun defaultResult() = CSTEmpty

    companion object {
        enum class Keyword { INTERFACE, WIRE, FUNCTION, DECLARE, IF, ELSE, NULL, TRUE, FALSE, IN, OUT, INOUT }

        enum class Operator { ADD, SUBTRACT, MULTIPLY, DIVIDE, EQUALS, NOT_EQUALS, GREATER_THAN, LESS_THAN, GREATER_THAN_EQUALS, LESS_THAN_EQUALS, CONNECTOR, COMMA, DOT, LOGICAL_AND, LOGICAL_OR }

        fun visitId(token: Token?): String {
            return token!!.text!!
        }

        fun visitIntLiteral(token: Token?): Int {
            return token!!.text!!.toInt()
        }

        fun keywordFrom(token: Token?): Keyword {
            return when (token?.type) {
                CSTLexer.Tokens.Interface -> Keyword.INTERFACE
                CSTLexer.Tokens.Wire -> Keyword.WIRE
                CSTLexer.Tokens.Function -> Keyword.FUNCTION
                CSTLexer.Tokens.Declare -> Keyword.DECLARE
                CSTLexer.Tokens.If -> Keyword.IF
                CSTLexer.Tokens.Else -> Keyword.ELSE
                CSTLexer.Tokens.Null -> Keyword.NULL
                CSTLexer.Tokens.True -> Keyword.TRUE
                CSTLexer.Tokens.False -> Keyword.FALSE
                CSTLexer.Tokens.In -> Keyword.IN
                CSTLexer.Tokens.Out -> Keyword.OUT
                CSTLexer.Tokens.Inout -> Keyword.INOUT
                else -> throw IllegalArgumentException("Unexpected token $token")
            }
        }

        fun operatorFrom(token: Token?): Operator {
            return when (token?.type) {
                CSTLexer.Tokens.Add -> Operator.ADD
                CSTLexer.Tokens.Subtract -> Operator.SUBTRACT
                CSTLexer.Tokens.Multiply -> Operator.MULTIPLY
                CSTLexer.Tokens.Divide -> Operator.DIVIDE
                CSTLexer.Tokens.Equals -> Operator.EQUALS
                CSTLexer.Tokens.NotEquals -> Operator.NOT_EQUALS
                CSTLexer.Tokens.AngleL -> Operator.LESS_THAN
                CSTLexer.Tokens.AngleR -> Operator.GREATER_THAN
                CSTLexer.Tokens.GreaterThanEquals -> Operator.GREATER_THAN_EQUALS
                CSTLexer.Tokens.LessThanEquals -> Operator.LESS_THAN_EQUALS
                CSTLexer.Tokens.Connector -> Operator.CONNECTOR
                CSTLexer.Tokens.Comma -> Operator.COMMA
                CSTLexer.Tokens.Dot -> Operator.DOT
                CSTLexer.Tokens.LogicalAnd -> Operator.LOGICAL_AND
                CSTLexer.Tokens.LogicalOr -> Operator.LOGICAL_OR
                else -> throw IllegalArgumentException("Unexpected token $token")
            }
        }
    }
}