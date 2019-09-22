grammar DummyLanguage;
compilation_unit : sentence+;

sentence : words Period;
words : (Whitespace Word Comma?)+;

Word : ('a'..'z'|'A'..'Z' | '_')('a'..'z'|'A'..'Z'|'0'..'9' | '_')*;
Period : '.';
Comma : ',';

Whitespace : (' '|'\t'|'\n'|'\r')+ -> channel(2);
