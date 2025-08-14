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

// Punctuation
ParanL: '(';
ParanR: ')';
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

accessor:
      SquareL expression SquareR #vectorItemAccessor
    | SquareL expression Colon expression SquareR #vectorSliceAccessor
    | Dot portIdentifier=Id #memberAccessor
;

expression:
      atom #atomExpression
    | Wire #wireExpression
    | False #trueExpression
    | True #trueExpression
    | IntLiteral #literalExpression
    | expression accessor #accessorExpression
    | ParanL expression ParanR #paranExpression
    | expression op=(Multiply|Divide) expression #multiplicaitonExpression
    | expression op=(Add|Subtract) expression #additionExpression
    | expression op=(AngleR|AngleL|GreaterThanEquals|LessThanEquals) expression #relationOperator
    | expression op=(Equals|NotEquals) expression #equalityOperator
    | expression LogicalAnd expression #andOperator
    | expression LogicalOr expression #orOperator
;

recordTransformerEntry: portIdentifier=Id Colon circuitExpression;
vectorTransformerEntry: index=expression Colon circuitExpression;

recordTransformer: CurlyL recordTransformerEntry* CurlyR;
vectorTransformer: SquareL vectorTransformerEntry* SquareR;

transformer: recordTransformer | vectorTransformer;

circuitExpressionType: transformer? interfaceType=expression;

circuitExpression:
      expression #loneCircuitExpression
    | Declare identifier=Id Colon circuitExpressionType #declaredCircuitExpression
    | expression Comma expression #commaExpression
    | expression Connector expression #connectorExpression
;

interfaceValues: AngleL (expression Comma)* expression? AngleR;
parameterValues: ParanL (expression Comma)* expression ParanR;

atom: identifier=Id interfaceValues? parameterValues?;

genericInterfaceDefinition: declaredIdentifier=Id;
genericInterfaceDefinitions: AngleL (genericInterfaceDefinition Comma)* genericInterfaceDefinition? AngleR;
genericParameterDefinitionTypeInterfaceList: (expression Comma)* expression?;
genericParameterDefinitionType: Id | genericParameterDefinitionTypeInterfaceList Connector genericParameterDefinitionTypeInterfaceList;
genericParameterDefinition: declaredIdenfier=Id Colon type=genericParameterDefinitionType;
genericParameterDefinitions: ParanL (genericParameterDefinition Comma)* genericParameterDefinition ParanR;

aliasInterfaceDefinition:
    Interface declaredIdentifer=Id
    genericInterfaceDefinitions?
    genericParameterDefinitions?
    expression
;

portDefinition: declaredIdentifier=Id Colon expression SemiColon;

recordInterfaceDefinition:
    Interface declaredIdentifer=Id
    genericInterfaceDefinitions?
    genericParameterDefinitions?
    CurlyL portDefinition* CurlyR
;

interfaceDefinition: aliasInterfaceDefinition | recordInterfaceDefinition;

functionIO: declaredIdentifier=Id Colon expression;
functionIOList: (functionIO Comma)* functionIO?;

conditionalCircuitBody: circuitStatement | CurlyL circuitStatement* CurlyR;

conditionalCircuit:
    If ParanL predicate=expression ParanR ifBody=conditionalCircuitBody
    (Else elseBody=conditionalCircuitBody)?
;

circuitStatement:
      conditionalCircuit #conditionalCircuitStatement
    | circuitExpression SemiColon #nonConditionalCircuitStatement
;

functionDefinition:
    Function declaredIdentifier=Id
    genericInterfaceDefinitions?
    genericParameterDefinitions?
    input=functionIOList Connector output=functionIOList
    CurlyL (circuitStatement)* CurlyR
;

