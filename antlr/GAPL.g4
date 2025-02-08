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

WhiteSpace: [ \t\r\n]+ -> skip;
IntLiteral: [0-9]+;
Id: [a-zA-Z_] [a-zA-Z0-9_]*;

/**
 * GRAMMAR RULES
 */

program: (interfaceDefinition | functionDefinition)+;

// Static Expressions
staticExpression: // TODO: This gramar currently ignores precendece
      True #trueStaticExpression
    | False #falseStaticExpression
    | IntLiteral #intLiteralStaticExpression
    | Id #idStaticExpression
    | ParanL staticExpression ParanR #paranStaticExpression
    | lhs=staticExpression Add rhs=staticExpression #addStaticExpression
    | lhs=staticExpression Subtract rhs=staticExpression #subtractStaticExpression
    | lhs=staticExpression Multiply rhs=staticExpression #multiplyStaticExpression
    | lhs=staticExpression Divide rhs=staticExpression #divideStaticExpression
    | lhs=staticExpression Equals rhs=staticExpression #equalsStaticExpression
    | lhs=staticExpression NotEquals rhs=staticExpression #notEqualsStaticExpression
    | lhs=staticExpression AngleL rhs=staticExpression #lessThanStaticExpression
    | lhs=staticExpression AngleR rhs=staticExpression #greaterThanStaticExpression
    | lhs=staticExpression LessThanEquals rhs=staticExpression #lessThanEqualsStaticExpression
    | lhs=staticExpression GreaterThanEquals rhs=staticExpression #greaterThanEqualsStaticExpression
;

// Helpers
genericInterfaceDefinitionList: (AngleL (Id Comma)* Id? AngleR)?;

genericParameterDefinitionList: ParanL (genericParameterDefinition Comma)* genericParameterDefinition? ParanR | ParanL ParanR;

genericParameterDefinition: Parameter identifier=Id Colon typeIdentifier=Id;

genericInterfaceValueList: (interfaceExpression Comma)* interfaceExpression?;

genericParameterValueList: (staticExpression Comma)* staticExpression?;

instantiation: Id (AngleL genericInterfaceValueList AngleR)? ParanL genericParameterValueList ParanR;

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
    (Colon inheritList)?
    CurlyL portDefinitionList CurlyR;

// Interface Definition Helpers

inheritList: interfaceExpression (Comma interfaceExpression)* Comma?;

portDefinitionList: portDefinition*;

portDefinition: Id Colon interfaceExpression SemiColon;

// Interface Expressions
interfaceExpression:
      Wire #wireInterfaceExpression
    | instantiation #definedInterfaceExpression
    | interfaceExpression SquareL staticExpression SquareR #vectorInterfaceExpression
    | Id #identifierInterfaceExpression
;

/* FUNCTIONS */

// Function Definition
functionDefinition:
    functionType Function Id
    genericInterfaceDefinitionList
    genericParameterDefinitionList
    input=functionIOList Connector output=functionIOList
    CurlyL (circuitStatement)* CurlyR;

functionType: (Sequential | Combinational)?;

functionIOList:
      Null #emptyFunctionIOList
    | functionIO (Comma functionIO)* Comma? #nonEmptyFunctionIOList;

functionIO: functionIOType Id Colon interfaceExpression;

functionIOType: (Sequential | Combinational)?;

// Circuit Statement
circuitStatement:
      conditionalCircuit #conditionalCircuitStatement
    | circuitExpression SemiColon #nonConditionalCircuitStatement
;

conditionalCircuit:
    If ParanL predicate=staticExpression ParanR ifBody=conditionalCircuitBody
    (Else elseBody=conditionalCircuitBody)?;

conditionalCircuitBody: circuitStatement | CurlyL circuitStatement* CurlyR;

circuitExpression: circuitConnectorExpression;

circuitConnectorExpression: circuitGroupExpression (Connector circuitGroupExpression)*;

circuitGroupExpression: circuitNodeExpression (Comma circuitNodeExpression)* Comma?;

circuitNodeExpression:
      Declare Id Colon interfaceExpression #declaredInterfaceCircuitExpression
    | Id singleAccessOperation* multipleArrayAccessOperation? #referenceCircuitExpression
    | ParanL circuitExpression ParanR #paranCircuitExpression
    | CurlyL (circuitStatement)* CurlyR #recordInterfaceConstructorCircuitExpression
    // TODO: vector interface constructor
;

singleAccessOperation:
      Dot Id #memberAccessOperation
    | SquareL staticExpression SquareR #singleArrayAccessOperation
;

multipleArrayAccessOperation: SquareL startIndex=staticExpression Colon endIndex=staticExpression SquareR;