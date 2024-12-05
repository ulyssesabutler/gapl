grammar GAPL;

/**
 * TOKENS
 */

// Keywords
Interface: 'interface';
Parameter: 'parameter';
Wire: 'wire';
Function: 'function';
Combinational: 'combinational';
Sequential: 'sequential';
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

WhiteSpace: [ \t\r\n]+ -> skip;
IntLiteral: [0-9]+;
Id: [a-zA-Z_] [a-zA-Z0-9_]*;

/**
 * GRAMMAR RULES
 */

program: (interfaceDefinition | functionDefinition)+;

// Static Expressions
staticExpression:
      True
    | False
    | IntLiteral
    | Id
    | ParanL staticExpression ParanR
    | staticExpression Add staticExpression
    | staticExpression Subtract staticExpression
    | staticExpression Multiply staticExpression
    | staticExpression Divide staticExpression
    | staticExpression Equals staticExpression
    | staticExpression NotEquals staticExpression
    | staticExpression AngleL staticExpression
    | staticExpression AngleR staticExpression
    | staticExpression LessThanEquals staticExpression
    | staticExpression GreaterThanEquals staticExpression
;

// Helpers
genericInterfaceDefinitionList: (AngleL genericInterfaceDefinitionListValues AngleR)?;

genericInterfaceDefinitionListValues: (Id Comma)* Id?;

genericParameterDefinitionList: ParanL genericParameterDefinitionListValues ParanR | ParanL ParanR;

genericParameterDefinitionListValues: (Parameter Id Colon Id)*;

genericInterfaceValueList: (interfaceExpression Comma)* interfaceExpression?;

genericParameterValueList: (staticExpression Comma)* staticExpression?;

instantiation: Id (AngleL genericInterfaceValueList AngleR) ParanL genericParameterValueList ParanR;

/* INTERFACES */

// Interface Definitions
interfaceDefinition: aliasInterfaceDefinition | recordInterfaceDefinition;

aliasInterfaceDefinition:
    Interface Id
    genericInterfaceDefinitionList
    genericParameterDefinitionList
    interfaceExpression;

recordInterfaceDefinition:
    Interface Id
    genericInterfaceDefinitionList
    genericParameterDefinitionList
    (Colon inheritList)*
    CurlyL portDefinitionList CurlyR;

// Interface Definition Helpers

inheritList: interfaceExpression (Comma interfaceExpression)*;

portDefinitionList: portDefinition*;

portDefinition: Id Colon interfaceExpression SemiColon;

// Interface Expressions
interfaceExpression: Wire | instantiation | interfaceExpression SquareL staticExpression SquareR | Id;
