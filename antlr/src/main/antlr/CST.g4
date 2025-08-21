grammar CST;

/**
 * TOKENS
 */

// Keywords
Interface: 'interface';
Wire: 'wire';
Function: 'function';
Declare: 'declare';
If: 'if';
Else: 'else';
Null: 'null';
True: 'true';
False: 'false';
In: 'in';
Out: 'out';
Inout: 'inout';

// Punctuation
ParenL: '(';
ParenR: ')';
CurlyL: '{';
CurlyR: '}';
SquareL: '[';
SquareR: ']';
AngleL: '<';
AngleR: '>';

Colon: ':';
SemiColon: ';';

// Operators
Add: '+';
Subtract: '-';
Multiply: '*';
Divide: '/';
Equals: '==';
NotEquals: '!=';
GreaterThanEquals: '>=';
LessThanEquals: '<=';
Connector: '=>';
Comma: ',';
Dot: '.';
LogicalAnd: '&&';
LogicalOr: '||';

LineComment: '//' ~[\r\n]* -> skip;
BlockComment: '/*' .*? '*/' -> skip;

WhiteSpace: [ \t\r\n]+ -> skip;
IntLiteral: [0-9]+;
Id: [a-zA-Z_] [a-zA-Z0-9_]*;

program: (interfaceDefinition | functionDefinition)+;

atom: identifier=Id genericInterfaceValues? genericParameterValues?;

accessor:
      SquareL index=expression SquareR #vectorItemAccessor
    | SquareL startIndex=expression Colon endIndex=expression SquareR #vectorSliceAccessor
    | Dot portIdentifier=Id #memberAccessor
;

expression:
      atom #atomExpression
    | Wire #wireExpression
    | True #trueExpression
    | False #falseExpression
    | value=IntLiteral #literalExpression
    | expression accessor #accessorExpression
    | ParenL expression ParenR #parenExpression
    | lhs=expression op=(Multiply|Divide) rhs=expression #multiplicaitonExpression
    | lhs=expression op=(Add|Subtract) rhs=expression #additionExpression
    | lhs=expression op=(AngleR|AngleL|GreaterThanEquals|LessThanEquals) rhs=expression #relationalExpression
    | lhs=expression op=(Equals|NotEquals) rhs=expression #equalityExpression
    | lhs=expression LogicalAnd rhs=expression #logicalAndExpression
    | lhs=expression LogicalOr rhs=expression #logicalOrExpression
;

recordTransformerEntry: portIdentifier=Id Colon circuitExpression;
vectorTransformerEntry: index=expression Colon circuitExpression;

transformer:
      CurlyL recordTransformerEntry* CurlyR #recordTransformer
    | SquareL vectorTransformerEntry* SquareR #vectorTransformer
;

circuitExpressionType:
      type=expression #basicCircuitExpressionType
    | transformer transformerType=(In|Out|Inout) interfaceType=expression #transformerCircuitExpressionType
;

circuitExpression: circuitGroupExpression (Connector circuitGroupExpression)*;

circuitGroupExpression: circuitNodeExpression (Comma circuitNodeExpression)* Comma?;

circuitNodeExpression:
      expression #loneCircuitExpression
    | Declare declaredIdentifier=Id Colon circuitExpressionType #declaredCircuitExpression
    | ParenL circuitExpression ParenL #parenCircuitExpression
;

genericInterfaceValues: AngleL (expression Comma)* expression? AngleR;
genericParameterValues: ParenL (expression Comma)* expression ParenR;

genericInterfaceDefinition: declaredIdentifier=Id;
genericInterfaceDefinitions: AngleL (genericInterfaceDefinition Comma)* genericInterfaceDefinition? AngleR;
genericParameterDefinitionTypeInterfaceList: (expression Comma)* expression?;
genericParameterDefinitionType:
      typeName=Id #namedGenericParameterDefinitionType
    | inputs=genericParameterDefinitionTypeInterfaceList Connector outputs=genericParameterDefinitionTypeInterfaceList #functionGenericParameterDefinitionType
;
genericParameterDefinition: declaredIdenfier=Id Colon type=genericParameterDefinitionType;
genericParameterDefinitions: ParenL (genericParameterDefinition Comma)* genericParameterDefinition ParenR;

aliasInterfaceDefinition:
    Interface declaredIdentifer=Id
    genericInterfaceDefinitions?
    genericParameterDefinitions?
    expression
;

portDefinition: declaredIdentifier=Id Colon interfaceType=expression SemiColon;

recordInterfaceDefinition:
    Interface declaredIdentifer=Id
    genericInterfaceDefinitions?
    genericParameterDefinitions?
    // TODO: Inherits
    CurlyL portDefinition* CurlyR
;

interfaceDefinition: aliasInterfaceDefinition | recordInterfaceDefinition;

functionIO: declaredIdentifier=Id Colon interfaceType=expression;
functionIOList: (functionIO Comma)* functionIO?;

conditionalCircuitBody: circuitStatement | CurlyL circuitStatement* CurlyR;

conditional:
    If ParenL predicate=expression ParenR ifBody=conditionalCircuitBody
    (Else elseBody=conditionalCircuitBody)?
;

circuitStatement:
      conditional #conditionalCircuitStatement
    | circuitExpression SemiColon #nonConditionalCircuitStatement
;

functionDefinition:
    Function declaredIdentifier=Id
    genericInterfaceDefinitions?
    genericParameterDefinitions?
    input=functionIOList Connector output=functionIOList
    CurlyL (circuitStatement)* CurlyR
;

