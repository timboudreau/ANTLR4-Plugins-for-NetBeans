lexer grammar MarkdownLexer;

/*
A work in progress, far from usable, but showed up some formatting
problems with formatting mode names, so including it here.
*/
 OpenHeading :
             POUND+ INLINE_WHITESPACE* -> pushMode(HEADING);

OpenPara :
         (LETTER | DIGIT) -> more,
             pushMode(PARAGRAPH);

OpenBulletList :
               INLINE_WHITESPACE+
                   ASTERISK
                   INLINE_WHITESPACE -> more,
                   pushMode(LIST);

OpenNumberedList :
                 INLINE_WHITESPACE+ DIGIT+
                     DOT
                     INLINE_WHITESPACE -> more,
                     pushMode(LIST);

OpenBlockQuote :
               GT -> pushMode(BLOCKQUOTE);

Whitespace :
           ALL_WS
           ;

Underline :
          NEWLINE DASH DASH DASH+ NEWLINE
          ;

DoubleUnderline :
                NEWLINE EQUALS EQUALS
                    EQUALS+ NEWLINE
                ;
mode BLOCKQUOTE;

BlockQuote :
           BlockQuoteBody -> popMode;

BlockQuoteBody :
               (LETTER | DIGIT) -> more,
                   pushMode(PARAGRAPH);
mode LIST;

ListItem :
         (ListItemBulletHead |
             ListItemNumberedHead)
             ListItemBody
         ;

ListItemBody :
             (LETTER | DIGIT) -> more,
                 pushMode(LIST_ITEM);

ListItemBulletHead :
                   BulletHeader
                       INLINE_WHITESPACE
                   ;

ListItemNumberedHead :
                     NumberHeader DOT
                         INLINE_WHITESPACE
                     ;

BulletHeader :
             INLINE_WHITESPACE+ ASTERISK {


 System.out.println("Bullet header"); }
             ;

NumberHeader :
             INLINE_WHITESPACE+ DIGIT+ {


 System.out.println("Number header"); }
             ;
mode LIST_ITEM;

ListItemContent :
                ListItemInnerContent -> popMode;

ListItemInnerContent :
                     (LETTER | DIGIT) -> more,
                         pushMode(PARAGRAPH);

// Heaading Mode
mode HEADING;

HeadingContent :
               HeadingWordLike
                   INLINE_WHITESPACE*
                   PUNCTUATION*
                   INLINE_WHITESPACE* (HeadingWordLike
                   INLINE_WHITESPACE*
                   PUNCTUATION*
                   INLINE_WHITESPACE*)
               ;

HeadingClose :
             NEWLINE -> popMode;

HeadingWordLike :
                (LETTER | DIGIT)(LETTER |
                    DIGIT)+
                ;

// Paragraph mode
mode PARAGRAPH;

ParaItalic :
           UNDERSCORE
           ;

ParaBold :
         ASTERISK
         ;

ParaStrikethrough :
                  STRIKE
                  ;

ParaCode :
         BACKTICK
         ;

ParaBracketOpen :
                OPEN_BRACKET
                ;

ParaBracketClose :
                 CLOSE_BRACKET
                 ;

ParaLink :
         LINK_TEXT+ COLON SLASH SLASH?
             LINK_TEXT+
         ;

ParaOpenParen :
              OPEN_PAREN
              ;

ParaCloseParen :
               CLOSE_PAREN
               ;

ParaWords :
          ParaWhitespace? WORDS+
              ParaWhitespace?
          ;

ParaWhitespace :
               INLINE_WHITESPACE
               ;

ParaClose :
          (INLINE_WHITESPACE? (EOF |
              DOUBLE_NEWLINE | NEWLINE))
              -> popMode;

/*
Heading : (POUND WS Words (NEWLINE | DOUBLE_NEWLINE));

Words : WordLike WS* PUNCTUATION* WS* (WordLike WS* PUNCTUATION* WS*) ;

*/
fragment WORDS :
               WORD_LIKE PUNC2?
                   INLINE_WHITESPACE??
               ;

fragment WORD_LIKE :
                   (LETTER | DIGIT)(LETTER
                       | DIGIT | PUNC2)+
                   ;

fragment PUNC2 :
               COLON
               | SEMICOLON
               | PLUS
               | BANG
               | AT
               | DOT
               | COMMA
               | DOLLARS
               | PERCENT
               | CAREN
               | AMPERSAND
               | DASH
               | EQUALS
               | OPEN_BRACE
               | CLOSE_BRACE
               | SQUOTE
               | DQUOTE
               | OPEN_BRACE
               | CLOSE_BRACE
               ;

fragment PUNCTUATION :
                     [\p{Punctuation}]
                     ;

fragment LETTER :
                [a-zA-Z\u0080-\u00FF]
                ;

fragment DIGIT :
               '0'..'9'
               ;

fragment ALL_WS :
                INLINE_WHITESPACE
                | NEWLINE
                ;

fragment DOUBLE_NEWLINE :
                        INLINE_WHITESPACE*
                            NEWLINE
                            INLINE_WHITESPACE*
                            NEWLINE (INLINE_WHITESPACE*
                            NEWLINE+)?
                        ;

fragment NEWLINE :
                 '\n'
                 ;

fragment SPECIAL_CHARS :
                       (ASTERISK | STRIKE
                           | BACKTICK |
                           POUND | STRIKE)
                       ;

fragment LINK_TEXT :
                   [a-zA-Z0-9]
                   | '/'
                   | '.'
                   ;

fragment PRE :
             '```'
             ;

fragment INLINE_WHITESPACE :
                           (' ' | '\t' |
                               '\r')
                           ;

fragment BACKSLASH :
                   '\\'
                   ;

fragment SQUOTE :
                '\''
                ;

fragment DQUOTE :
                '"'
                ;

fragment OPEN_PAREN :
                    '('
                    ;

fragment CLOSE_PAREN :
                     ')'
                     ;

fragment OPEN_BRACE :
                    '{'
                    ;

fragment CLOSE_BRACE :
                     '}'
                     ;

fragment OPEN_BRACKET :
                      '['
                      ;

fragment CLOSE_BRACKET :
                       ']'
                       ;

fragment COLON :
               ':'
               ;

fragment AMPERSAND :
                   '&'
                   ;

fragment COMMA :
               ','
               ;

fragment PLUS :
              '+'
              ;

fragment DOLLARS :
                 '$'
                 ;

fragment PERCENT :
                 '%'
                 ;

fragment CAREN :
               '^'
               ;

fragment AT :
            '@'
            ;

fragment BANG :
              '!'
              ;

fragment GT :
            '>'
            ;

fragment LT :
            '<'
            ;

fragment QUESTION :
                  '?'
                  ;

fragment SEMICOLON :
                   ';'
                   ;

fragment SLASH :
               '/'
               ;

fragment UNDERSCORE :
                    '_'
                    ;

fragment STRIKE :
                '~~'
                ;

fragment BACKTICK :
                  '`'
                  ;

fragment DASH :
              '-'
              ;

fragment EQUALS :
                '='
                ;

fragment ASTERISK :
                  '*'
                  ;

fragment POUND :
               '#'
               ;

fragment DOT :
             '.'
             ;