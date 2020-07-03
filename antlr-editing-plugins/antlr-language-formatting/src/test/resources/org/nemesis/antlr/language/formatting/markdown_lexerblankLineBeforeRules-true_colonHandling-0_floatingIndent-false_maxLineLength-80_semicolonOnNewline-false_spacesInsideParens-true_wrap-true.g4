lexer grammar MarkdownLexer;

OpenHeading : POUND+ INLINE_WHITESPACE* -> pushMode ( HEADING );

OpenPara : ( LETTER | DIGIT | OPEN_BRACE | OPEN_PAREN ) -> more, pushMode ( PARAGRAPH );

OpenBulletList : INLINE_WHITESPACE+ ASTERISK -> more, pushMode ( LIST );

OpenNumberedList : INLINE_WHITESPACE+ DIGIT+ DOT -> more, pushMode ( LIST );

OpenBlockQuote : INLINE_WHITESPACE* GT -> pushMode ( BLOCKQUOTE );

Whitespace : ALL_WS;

HorizontalRule : ( DASH DASH DASH+ NEWLINE )| ( ASTERISK ASTERISK ASTERISK+
        NEWLINE )| ( DASH SPACE DASH SPACE ( DASH SPACE )+ NEWLINE )| ( ASTERISK
        SPACE ASTERISK SPACE ( ASTERISK SPACE )+ SPACE? NEWLINE );

/*
Underline
    : NEWLINE DASH DASH DASH+ NEWLINE;

DoubleUnderline
    : NEWLINE EQUALS EQUALS EQUALS+ NEWLINE;
*/
mode PARAGRAPH;

ParaLink : LINK_TEXT+ COLON SLASH SLASH? LINK_TEXT+;

ParaWords : WORD_LIKE PUNC2? ( INLINE_WHITESPACE*? WORD_LIKE PUNC2? )*;

ParaNextBullet : ( ParaDoubleNewline | ParaNewLine )? INLINE_WHITESPACE ASTERISK
        -> more, mode ( LIST );

ParaBreak : ParaDoubleNewline -> popMode;

ParaDoubleNewline : INLINE_WHITESPACE* NEWLINE INLINE_WHITESPACE* NEWLINE ( INLINE_WHITESPACE*?
        NEWLINE )*;

ParaInlineWhitespace : INLINE_WHITESPACE+;

ParaNewLine : NEWLINE;

ParaItalic : UNDERSCORE;

ParaBold : ASTERISK;

ParaStrikethrough : STRIKE;

ParaCode : BACKTICK;

ParaBracketOpen : OPEN_BRACKET;

ParaBracketClose : CLOSE_BRACKET;

ParaOpenParen : OPEN_PAREN;

ParaCloseParen : CLOSE_PAREN;

ParaEof : EOF -> more, popMode;


mode BLOCKQUOTE;

BlockQuotePrologue : INLINE_WHITESPACE;

BlockQuote : NON_WHITESPACE -> more, mode ( PARAGRAPH );

BlockquoteDoubleNewline : INLINE_WHITESPACE* NEWLINE INLINE_WHITESPACE* NEWLINE ( INLINE_WHITESPACE*?
        NEWLINE )* -> popMode;


mode LIST;

ListPrologue : DOUBLE_NEWLINE? INLINE_WHITESPACE;

ListItem : ( LETTER | DIGIT ) -> more, pushMode ( PARAGRAPH );

NestedListItemHead : (( INLINE_WHITESPACE+ ASTERISK )| ( INLINE_WHITESPACE+ DIGIT+
        DOT )) INLINE_WHITESPACE;

CloseList : DOUBLE_NEWLINE -> popMode;

//    : DOUBLE_NEWLINE -> mode(DEFAULT_MODE);
CloseList2 : NEWLINE NON_WHITESPACE -> more, popMode;


mode HEADING;

/*
HeadingContent
    : HeadingWordLike INLINE_WHITESPACE* PUNCTUATION* INLINE_WHITESPACE* ( HeadingWordLike
        INLINE_WHITESPACE* PUNCTUATION* INLINE_WHITESPACE* )+;
*/
 HeadingContent : WORD_LIKE PUNCTUATION? ( INLINE_WHITESPACE*? WORD_LIKE
        PUNCTUATION? )*;

HeadingClose : NEWLINE -> mode ( DEFAULT_MODE );

HeadingWordLike : ( LETTER | DIGIT ) ( LETTER | DIGIT )+;

/*
Heading : (POUND WS Words (NEWLINE | DOUBLE_NEWLINE));

Words : WordLike WS* PUNCTUATION* WS* (WordLike WS* PUNCTUATION* WS*) ;

*/
fragment WORDS : INLINE_WHITESPACE? WORD_LIKE PUNC2? INLINE_WHITESPACE??;

fragment WORD_LIKE : ( LETTER | DIGIT ) ( LETTER | DIGIT | PUNC2 )*;

fragment PUNC2 : [><{}/\\:;+!@.,$%^&\-='"?];

fragment PUNCTUATION : [\p{Punctuation}];

fragment LETTER : [\p{Alphabetic}];

fragment DIGIT : [\p{Digit}];

//    : '0'..'9';
fragment ALL_WS : INLINE_WHITESPACE | NEWLINE;

fragment DOUBLE_NEWLINE : NEWLINE NEWLINE NEWLINE*?;

fragment NEWLINE : '\n';

fragment SPECIAL_CHARS : ASTERISK | STRIKE | BACKTICK | POUND | STRIKE;

fragment NON_WHITESPACE : ~[ \r\n\t];

//    :~[\p{White_Space}];
fragment LINK_TEXT : [a-zA-Z0-9] | '/' | '.';

fragment PRE : '```';

fragment INLINE_WHITESPACE : SPACE | TAB | CARRIAGE_RETURN;

fragment SPACE : ' ';

fragment TAB : '\t';

fragment CARRIAGE_RETURN : '\r';

fragment BACKSLASH : '\\';

fragment SQUOTE : '\'';

fragment DQUOTE : '"';

fragment OPEN_PAREN : '(';

fragment CLOSE_PAREN : ')';

fragment OPEN_BRACE : '{';

fragment CLOSE_BRACE : '}';

fragment OPEN_BRACKET : '[';

fragment CLOSE_BRACKET : ']';

fragment COLON : ':';

fragment AMPERSAND : '&';

fragment COMMA : ',';

fragment PLUS : '+';

fragment DOLLARS : '$';

fragment PERCENT : '%';

fragment CAREN : '^';

fragment AT : '@';

fragment BANG : '!';

fragment GT : '>';

fragment LT : '<';

fragment QUESTION : '?';

fragment SEMICOLON : ';';

fragment SLASH : '/';

fragment UNDERSCORE : '_';

fragment STRIKE : '~~';

fragment BACKTICK : '`';

fragment DASH : '-';

fragment EQUALS : '=';

fragment ASTERISK : '*';

fragment POUND : '#';

fragment DOT : '.';