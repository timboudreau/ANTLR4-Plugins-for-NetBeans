lexer grammar basics;

fragment WORDS
    : INLINE_WHITESPACE? ( WORD_LIKE | NUMBER_LIKE ) SAFE_PUNCTUATION?
        INLINE_WHITESPACE??;

// allow a single underscore or asterisk in a word, i.e. _FOO_BAR_ is "FOO_BAR" italicized, 
// but "_FOO_BAR" is "FOO_BAR" with FOO italicized.
// Note the first alternative must not allow trailing punctuation, or for example,
// a sentence like "Make the wheel _rounder_." will get the first _ parsed as an
// opending italic token, and then "rounder_." treated as a word.
fragment WORD_LIKE
    : (( LETTER )+ ( LETTER | DIGIT | SAFE_PUNCTUATION )* ( LETTER | DIGIT |
        SAFE_PUNCTUATION | UNDERSCORE+ | ASTERISK+ )( LETTER | DIGIT )+ )
    | ( LETTER DIGIT )
    | ( LETTER )( LETTER | DIGIT | SAFE_PUNCTUATION )*;

fragment WORD_OR_NUMBER_LIKE
    : ( LETTER | DIGIT | PUNCTUATION )+;

fragment NUMBER_LIKE
    : DIGIT+;

fragment SAFE_PUNCTUATION
    : [><{}/\\:;,+!@.$%^&\-='"?¿¡]
    | '\\`'
    | '\\*';

fragment NON_BACKTICK
    :~[`]+;

fragment PUNCTUATION
    : [\p{Punctuation}];

fragment LETTER
    : [\p{Alphabetic}];

fragment DIGIT
    : [\p{Digit}];

fragment NON_HR
    :~[ -*];

fragment ALL_WS
    : INLINE_WHITESPACE
    | NEWLINE;

fragment DOUBLE_NEWLINE
    : NEWLINE NEWLINE NEWLINE*?;

fragment NEWLINE
    : '\n';

fragment SPECIAL_CHARS
    : ASTERISK
    | STRIKE
    | BACKTICK
    | POUND
    | STRIKE;

fragment NON_WHITESPACE
    :~[ \r\n\t];

//    :~[\p{White_Space}];
fragment LINK_TEXT
    : [a-zA-Z0-9\-_$/\\.!?+=&^%#];

fragment PRE
    : '```';

fragment INLINE_WHITESPACE
    : [ \r\t];

fragment SPACE
    : ' ';

fragment TAB
    : '\t';

fragment CARRIAGE_RETURN
    : '\r';

fragment BACKSLASH
    : '\\';

fragment SQUOTE
    : '\'';

fragment DQUOTE
    : '"';

fragment OPEN_PAREN
    : '(';

fragment CLOSE_PAREN
    : ')';

fragment OPEN_BRACE
    : '{';

fragment CLOSE_BRACE
    : '}';

fragment OPEN_BRACKET
    : '[';

fragment CLOSE_BRACKET
    : ']';

fragment COLON
    : ':';

fragment AMPERSAND
    : '&';

fragment COMMA
    : ',';

fragment PLUS
    : '+';

fragment DOLLARS
    : '$';

fragment PERCENT
    : '%';

fragment CAREN
    : '^';

fragment AT
    : '@';

fragment BANG
    : '!';

fragment GT
    : '>';

fragment LT
    : '<';

fragment QUESTION
    : '?';

fragment SEMICOLON
    : ';';

fragment SLASH
    : '/';

fragment UNDERSCORE
    : '_';

fragment STRIKE
    : '~~';

fragment BACKTICK
    : '`';

fragment DASH
    : '-';

fragment EQUALS
    : '=';

fragment ASTERISK
    : '*';

fragment POUND
    : '#';

fragment DOT
    : '.';
