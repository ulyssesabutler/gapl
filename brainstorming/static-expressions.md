# Static Expressions

## Data Types
```text
StaticExpressionTypeIdentifier ::=   StaticSizeExpressionTypeIdentifier
                                   | StaticBitArrayExpressionIdentifier
                                   | "boolean"
                                    
StaticSizeExpressionTypeIdentifier ::= "integer"

StaticBitArrayExpressionIdentifier ::= "bits" | StaticSizeExpressionTypeIdentifier
```

## Value Expressions
```text
StaticExpression se ::= StaticSizeExpression | StaticBitArrayExpression | StaticBooleanExpression
StaticSizeExpression sse ::= Integer | sse + sse | sse - sse | sse * sse | sse / sse | (sse)
StaticBooleanExpression sbe ::=   "true" | "false" | se == se | se != se | sse > sse | sse < sse | sse >= sse
                                | sse <= sse | (sbe) | sbe "and" sbe | sbe "or" sbe | sbe "xor" sbe | "not" sbe
```

For `StaticBitArrayExpressions`, we should also allow repeat operators, and specifying hex and binary values