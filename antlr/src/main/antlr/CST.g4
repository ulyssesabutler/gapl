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
Integer: 'integer';
View: 'view';
Of: 'of';

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
Remainder: '%';
Equals: '==';
NotEquals: '!=';
GreaterThanEquals: '>=';
LessThanEquals: '<=';
Connector: '=>';
Comma: ',';
Dot: '.';
At: '@';
LogicalAnd: '&&';
LogicalOr: '||';

LineComment: '//' ~[\r\n]* -> channel(HIDDEN);
BlockComment: '/*' .*? '*/' -> channel(HIDDEN);

WhiteSpace: [ \t\r\n]+ -> skip;
IntLiteral: [0-9]+;
Id: [a-zA-Z_] [a-zA-Z0-9_]*;

program: (interfaceDefinition | functionDefinition)+;

atom: identifier=Id parameterValues?;

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
    | lhs=expression op=(Multiply|Divide|Remainder) rhs=expression #multiplicaitonExpression
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

// A protocol annotation (type-system.md §5). On a function definition it names the function's protocol
// (none means @pure); on a call it marks an island - a coercion into the caller's protocol.
protocolAnnotation: At protocol=Id;

circuitNodeExpression:
      expression protocolAnnotation? #loneCircuitExpression
    | Declare declaredIdentifier=Id Colon circuitExpressionType protocolAnnotation? #declaredCircuitExpression
    | ParenL circuitExpression ParenR #parenCircuitExpression
;

parameterValues: ParenL (expression Comma)* expression? ParenR;

// TODO: Support null
parameterDefinitionTypeInterfaceList: (expression Comma)* expression?;
parameterDefinitionType:
      Integer #integerParameterDefinitionType
    | Interface #interfaceParameterDefinitionType
    | inputs=parameterDefinitionTypeInterfaceList Connector outputs=parameterDefinitionTypeInterfaceList #functionParameterDefinitionType
;
parameterDefinition: declaredIdenfier=Id Colon type=parameterDefinitionType;
parameterDefinitionList: ParenL (parameterDefinition Comma)* parameterDefinition? ParenR;

aliasInterfaceDefinition:
    Interface declaredIdentifer=Id
    parameterDefinitionList?
    expression
;

portDefinition: declaredIdentifier=Id Colon interfaceType=expression SemiColon;

recordInterfaceDefinition:
    Interface declaredIdentifer=Id
    parameterDefinitionList?
    // TODO: Inherits
    CurlyL portDefinition* CurlyR
;

// A view names bit ranges of a base interface. Each field's path is an accessor chain from the
// (implicit) base value, so a record member is written `.data[0:47]` and a vector base starts `[0]`.
viewFieldDefinition: declaredIdentifier=Id Colon accessor+ SemiColon;

viewInterfaceDefinition:
    View declaredIdentifer=Id
    parameterDefinitionList?
    Of base=expression
    CurlyL viewFieldDefinition* CurlyR
;

interfaceDefinition: aliasInterfaceDefinition | recordInterfaceDefinition | viewInterfaceDefinition;

functionIO: declaredIdentifier=Id Colon interfaceType=expression;
// TODO: Add null
functionIOList:
      Null
    | (functionIO Comma)* functionIO Comma?
;

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
    protocolAnnotation?
    Function declaredIdentifier=Id
    parameterDefinitionList?
    input=functionIOList Connector output=functionIOList
    CurlyL (circuitStatement)* CurlyR
;

