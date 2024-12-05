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
interfaceExpression:
      Wire
    | instantiation
    | interfaceExpression SquareL staticExpression SquareR
    | Id
;

/* FUNCTIONS */

// Function Definition
functionDefinition:
    functionType Function Id
    genericInterfaceDefinitionList
    genericParameterDefinitionList
    functionIOList Connector functionIOList
    CurlyL (circuitStatement)* CurlyR;

functionType: (Sequential | Combinational)?;

functionIOList: Null | (functionIOType Id Colon interfaceExpression)+;

functionIOType: (Sequential | Combinational)?;

// Circuit Statement
circuitStatement: conditionalCircuitStatement | (circuitExpression SemiColon);

conditionalCircuitStatement:
    If ParanL staticExpression ParanR (circuitStatement | CurlyL circuitStatement* CurlyR)
    (Else (circuitStatement | CurlyL circuitStatement* CurlyR))?;

circuitExpression: circuitConnectorExpression;

circuitConnectorExpression: circuitGroupExpression (Connector circuitGroupExpression)*;

circuitGroupExpression: circuitNodeExpression (Comma circuitNodeExpression)* Comma?;

circuitNodeExpression:
    | interfaceExpression
    | Id Colon interfaceExpression
    | Id (memberAccessOperation|singleArrayAccessOperation)* multipleArrayAccessOperation?
    | ParanL circuitExpression ParanR
    | circuitRecordInterfaceConstructorExpression
;

memberAccessOperation: Dot Id;

singleArrayAccessOperation: SquareL IntLiteral SquareR;

multipleArrayAccessOperation: SquareL IntLiteral Colon IntLiteral SquareR;

circuitRecordInterfaceConstructorExpression: CurlyL (circuitStatement)* CurlyR;