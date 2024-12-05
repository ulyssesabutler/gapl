grammar MyLanguage;

program: statement+ ;

statement: 'print' expression ';' ;

expression: STRING | NUMBER ;

STRING: '"' (~["\r\n])* '"' ;
NUMBER: [0-9]+ ;
WS: [ \t\r\n]+ -> skip ;