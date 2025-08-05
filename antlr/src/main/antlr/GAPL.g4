grammar GAPL;

/**
 * TOKENS
 */

// Keywords
Interface: 'interface';
Parameter: 'parameter';
Wire: 'wire';
Function: 'function';
Stream: 'stream';
Signal: 'signal';
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
ProtocolAccessor: '::';

LineComment: '//' ~[\r\n]* -> skip;
BlockComment: '/*' .*? '*/' -> skip;

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

genericParameterDefinition: Parameter identifier=Id Colon type=genericParameterType;

genericParameterType:
      Id #idGenericParameterType
    | Function input=abstractFunctionIOList Connector output=abstractFunctionIOList #functionGenericParameterType
;

genericInterfaceValueList: (interfaceExpression Comma)* interfaceExpression?;

// TODO: We should add support for named parameters as well as positional
genericParameterValueList: (genericParameterValue Comma)* genericParameterValue?;

genericParameterValue:
      staticExpression #staticExpressionGenericParameterValue
    | functionExpression #functionExpressionParameterValue
;

instantiation: Id (AngleL genericInterfaceValueList AngleR)? ParanL genericParameterValueList ParanR;

functionExpression:
      Function instantiation #functionExpressionInstantiation
    | Function functionIdentifier=Id #functionExpressionReference
;

transfomerMode:
      In #inTransformerMode // TODO: In is a bit special since it requires an expression for each item.
    | Out #outTransformerMode
    | Inout #inOutTransformerMode
;

/* INTERFACES */

// Interface Definitions
interfaceDefinition: aliasInterfaceDefinition | recordInterfaceDefinition;

aliasInterfaceDefinition:
    Interface Id
    genericInterfaceDefinitionList
    genericParameterDefinitionList
    interfaceExpression
;

recordInterfaceDefinition:
    Interface Id
    genericInterfaceDefinitionList
    genericParameterDefinitionList
    (Colon inheritList)?
    CurlyL portDefinitionList CurlyR
;

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
    Function Id
    genericInterfaceDefinitionList
    genericParameterDefinitionList
    input=functionIOList Connector output=functionIOList
    CurlyL (circuitStatement)* CurlyR
;

abstractFunctionIOList:
      Null #emptyAbstractFunctionIOList
    | abstractFunctionIO (Comma abstractFunctionIO)* Comma? #nonEmptyAbstractFunctionIOList
;

functionIOList:
      Null #emptyFunctionIOList
    | functionIO (Comma functionIO)* Comma? #nonEmptyFunctionIOList
;

abstractFunctionIO: interfaceExpression;

functionIO: Id Colon interfaceExpression;

// Circuit Statement
circuitStatement:
      conditionalCircuit #conditionalCircuitStatement
    | circuitExpression SemiColon #nonConditionalCircuitStatement
;

conditionalCircuit:
    If ParanL predicate=staticExpression ParanR ifBody=conditionalCircuitBody
    (Else elseBody=conditionalCircuitBody)?
;

conditionalCircuitBody: circuitStatement | CurlyL circuitStatement* CurlyR;

circuitExpression: circuitConnectorExpression;

circuitConnectorExpression: circuitGroupExpression (Connector circuitGroupExpression)*;

circuitGroupExpression: circuitNodeExpression (Comma circuitNodeExpression)* Comma?;

circuitNodeRecordTransformerExpression: portIdentifier=Id Colon circuitExpression SemiColon;
circuitNodeListTransformerExpression: index=staticExpression Colon circuitExpression SemiColon;

circuitNodeInterfaceTransformer:
      CurlyL circuitNodeRecordTransformerExpression* CurlyR #circuitNodeInterfaceRecordTransformer
    | SquareL circuitNodeListTransformerExpression* Comma? SquareR #circuitNodeInterfaceListTransformer
;

circuitNodeInterior:
      functionExpression #circuitNodeFunctionInterior
    | interfaceExpression #circuitNodeInterfaceInterior
    | circuitNodeInterfaceTransformer Colon transfomerMode interfaceExpression #circuitNodeInterfaceTransformerInterior
;

circuitNodeExpression:
      (Declare nodeIdentifier=Id Colon)? circuitNodeInterior #circuitNodeCreationExpression
    | nodeIdentifier=Id singleAccessOperation* multipleArrayAccessOperation? #circuitNodeReferenceExpression
    // TODO: This will require some kind of type inference to determine the wire width
    | staticExpression #circuitNodeLiteralExpression
;

singleAccessOperation:
      Dot Id #memberAccessOperation
    | SquareL staticExpression SquareR #singleArrayAccessOperation
;

multipleArrayAccessOperation: SquareL startIndex=staticExpression Colon endIndex=staticExpression SquareR;