grammar Sensors;

compilationUnit : 
    samplesDefault? 
    (probesDef | definitions | recipes | actions)+
    EOF
    ;

definitions : definition+;

recipes : recipe+;

actions : action+;

samplesDefault : K_SAMPLES num=UINT S_SEMI;

recipe : 
    K_RECIPE name=recipeName items=tasks S_SEMI;

tasks : 
    first=task (S_COMMA task)*;

task : 
    (waitTask | pollTask | closeTask);

definition : 
    K_DEF name=variable portType? val=UINT S_SEMI;

waitTask :
    K_WAIT ms=value;

pollTask :
    K_POLL what=value times=value?;

closeTask :
    K_CLOSE what=value;

value : (var=variable | ref=reference | num=numericValue);

reference : S_DOLLARS num=UINT;

action : (runAction | logAction | runAllAction) S_SEMI;

logAction : K_LOG stringValue;

runAction : K_RUN name=recipeName vals=value+ K_AS sensor=UINT;

portType : K_GPIO | K_ADC;

variable : UNCAPITALIZED_ID;

recipeName : CAPITALIZED_ID;

numericValue :
    UINT;

stringValue : L_STRING;

word : CAPITALIZED_ID | UNCAPITALIZED_ID | L_STRING | UINT;

probesDef : K_PROBES name=variable S_OPENBRACE probeDef (S_COMMA probeDef)* S_COMMA? S_CLOSEBRACE;

probeDef : sensorid=UINT S_COLON a=value S_SLASH b=value;

runAllAction : K_RUNALL name=variable probeRun (S_COMMA probeRun)*;

probeRun : name=recipeName offsetInfo?;

offsetInfo : K_OFFSET offsetBy=UINT;

K_RUNALL : 'runall';
K_PROBES : 'probes';
K_OFFSET : 'offset';
K_SAMPLES : 'samples';
K_POLL : 'poll';
K_WAIT : 'wait';
K_LOG : 'log';
K_RUN : 'run';
K_CLOSE : 'close';
K_RECIPE : 'recipe';
K_ADC : 'adc';
K_GPIO : 'gpio';
K_DEF : 'def';
K_AS: 'as';

UINT : DIGITS;

S_SEMI:';';
S_SLASH:'/';
S_X:'x';
S_DOLLARS:'$';
S_WHITESPACE : (' '|'\t'|'\n'|'\r')+ -> channel(2);
COMMENT : S_HASH .+? '\n' -> channel(3);
S_COMMA : ',';
S_HASH : '#';
S_COLON : ':';
S_OPENBRACE : '{';
S_CLOSEBRACE : '}';

L_STRING : STRING | STRING2;

CAPITALIZED_ID: ('A'..'Z')('a'..'z'|'A'..'Z'|'0'..'9' | '_' | '-')+;
UNCAPITALIZED_ID: ('a'..'z')('a'..'z'|'A'..'Z'|'0'..'9' | '_' | '-')+;

// e.g. 36b4f51b-f5d6-4c10-824b-971af191531c
UUID: HEX HEX HEX HEX HEX HEX HEX HEX 
    DASH HEX HEX HEX HEX
    DASH HEX HEX HEX HEX
    DASH HEX HEX HEX HEX
    DASH HEX HEX HEX HEX HEX HEX HEX HEX  HEX HEX HEX HEX;

fragment HEX : DIGIT | HEX_LETTER;
fragment DIGITS : DIGIT+;
fragment HEX_LETTER: [a-f] | [A-F];
fragment DIGIT: [0-9];
fragment STRING: '"' (ESC|.)*? '"';
fragment STRING2: '\''(ESC2|.)*? '\'';
fragment DASH : '-';
fragment ESC : '\\"' | '\\\\' ;
fragment ESC2 : '\\\'' | '\\\\' ;
