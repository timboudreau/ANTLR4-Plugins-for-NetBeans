grammar SimpleLanguage;

compilation_unit : 
    namespaceStatement?
    importStatement*?
    typesStatement
    typeDeclaration+
    EOF;

typeDeclaration : 
      (desc=description? name=typeName S_COLON kind=K_FLOAT (K_DEFAULT numericExpression)? S_SEMICOLON) #floatType
    | (desc=description? name=typeName S_COLON kind=K_INT (K_DEFAULT intExpression)?  S_SEMICOLON) #integerType
    | (desc=description? name=typeName S_COLON kind=K_INT_ARRAY (K_DEFAULT intArray)?  S_SEMICOLON) #integerArray
    | (desc=description? name=typeName S_COLON kind=K_STRING (K_DEFAULT L_STRING)? S_SEMICOLON) #stringType
    | (desc=description? name=typeName S_COLON kind=K_BOOLEAN (K_DEFAULT L_BOOLEAN)? S_SEMICOLON) #booleanType
    | (desc=description? name=typeName S_COLON kind=K_OBJECT S_OPEN_BRACE typeDeclaration+ S_CLOSE_BRACE) #objectType
    | (desc=description? name=typeName S_COLON kind=K_REFERENCE to=(ID | QUALIFIED_ID) S_SEMICOLON) #referenceType
    ;

description : lines=DESCRIPTION+;
namespaceStatement : K_NAMESPACE id=(ID | QUALIFIED_ID)  S_SEMICOLON;
importStatement : K_IMPORT id=(ID | QUALIFIED_ID)  S_SEMICOLON;
typesStatement : K_TYPE name=ID S_SEMICOLON;
typeName : ID;

intExpression : 
    L_INT #singleInt
    | (L_INT OP numericExpression) #mathInt
    | (S_OPEN_PARENS (L_INT OP numericExpression) S_CLOSE_PARENS) #parentheticInt
; 

numericExpression : (L_INT | L_FLOAT) #singleFloat
    | ((L_FLOAT | L_INT) OP numericExpression) #mathFloat
    | (S_OPEN_PARENS numericExpression S_CLOSE_PARENS) #parentheticFloat;


intArray :
    S_OPEN_BRACKET L_INT* S_CLOSE_BRACKET;

OP : S_PLUS | S_MINUS | S_SLASH | S_ASTERISK | S_PERCENT;
QUALIFIED_ID : ID (S_DOT ID)+;

K_IMPORT : 'import';
K_NAMESPACE : 'namespace';
K_INT : 'int';
K_INT_ARRAY : 'intArray';
K_OBJECT : 'object';
K_STRING : 'string';
K_FLOAT : 'float';
K_BOOLEAN : 'boolean';
K_TYPE : 'types';
K_DEFAULT : 'default';
K_REFERENCE : 'reference';

S_SLASH : '/';
S_ASTERISK : '*';
S_PERCENT : '%';
S_OPEN_PARENS : '(';
S_CLOSE_PARENS : ')';
S_OPEN_BRACE : '{';
S_CLOSE_BRACE : '}';
S_COLON : ':';
S_SEMICOLON : ';';
S_EQ : '=';
S_COMMA : ',';
S_DOT : '.';
S_OPEN_BRACKET : '[';
S_CLOSE_BRACKET : ']';

LINE_COMMENT : '//' .*? S_LINE_END -> channel(1);
COMMENT : '/*' .*? '*/' -> channel(1);

S_WHITESPACE : (' '|'\t'|'\n'|'\r')+ -> channel(2);

L_STRING : (STRING | STRING2);
L_FLOAT
    : (S_MINUS)? DIGIT+ (S_DOT DIGIT+);
L_INT : (S_MINUS)? DIGITS;
L_BOOLEAN : TRUE | FALSE;

DESCRIPTION: DESC_DELIMITER( ESC|.)*? S_LINE_END;

ID: ('a'..'z'|'A'..'Z' | '_')('a'..'z'|'A'..'Z'|'0'..'9' | '_')*;

fragment DESC_DELIMITER : '**';

fragment TRUE : 'true';
fragment FALSE : 'false';
fragment STRING: '"' (ESC|.)*? '"';
fragment STRING2: '\''(ESC2|.)*? '\'';
fragment DIGITS : DIGIT+;
fragment DIGIT: [0-9];
fragment S_PLUS : '+';
fragment S_MINUS : '-';
fragment ESC : '\\"' | '\\\\' ;
fragment ESC2 : '\\\'' | '\\\\' ;
fragment WS : ' ' | '\t' | '\n' | '\r';
fragment S_LINE_END : '\r'? '\n';
