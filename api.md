Antlr Languages API
===================

Antlr 4 is a marvelous tool for creating grammars and generating lexers and
parsers.  A generated Antlr lexer and parser contain most of the information
you would need to create basic language support in an IDE - mostly what is
needed is glue-code and adapter-code to translate what Antlr provides into
terms NetBeans understands.

This is what the APIs in the modules here are for.  


Patterns
--------


### Annotations and LOTS of Code Generation

Most of the "code" you write will be in the form of Java annotations - to get
basic syntax highlighting working, a single (large) annotation can take care
of that.


### Those generated fields on your lexer and parser are your friends

Antlr's generated classes contain

 * A generated `int`-typed field for every lexer rule, on the lexer class,
named for the rules as they appear in the grammar
 * A generated `int`-typed field for every parser rule, on the parser class
named, e.g., `RULE_method`
 * A `Vocabulary` object in a static field of the lexer, which contains information
about individual tokens

A lot of the annotations you will use involve mapping things like editor
colorings to lists of tokens.  The fields on the lexer and parser are what you
will use in these.

### You describe _what you want_ not _how to do it_ wherever possible

A pattern you will see in all of the APIs is that complex tasks are made
_as declarative as possible_ - for example, for each of 

 * Extracting regions of interest and identifying data from a parse tree

 * Defining code-reformatting rules and the conditions under which to apply them

 * Defining the conditions under which one or another code-completion provider
should be asked for suggestions

you implement a class that is passed a _builder_ of some sort;  you use it to
describe, as trees of predicates you build up, under what conditions a particular
action is to be run (and usually that action is something predefined).  So the
norm is not that you write code that does something to source code, but rather,
you describe to the system what to do and when to do it.

Ideally (though it is not an ideal world), the result should be as close to
a logic-free description you could turn into a language of its own as possible.

### Slightly Unusual Collection-like Data Structures

> "Binary search is so simple and robust that you can code up five subtly 
> different binary searches for different places in your code, all using 
> optimized direct references into your internal structures, and not 
> suffer any software engineering angst." - Tim Bray

All of these implement `Iterable` and other familiar interfaces where it makes
sense - but like Antlr itself, internally they are highly optimized, and 
typically operate over 1-2 `int[]`'s, use 
[binary search](https://www.tbray.org/ongoing/When/200x/2003/03/22/Binary) to
reply to queries.  It turns out
you can actually write a _multiply nested_ data structure with two arrays of
integers and a slight variant on traditional binary search.

All of this is code that needs to be able to be run over hundreds of files
in a minute, and may be called several times per-second as the user types, and
should neither create GC pressure, nor be noticable to the user.  Sometimes
there is no substitute for using *exactly* the right data structure for the
job.

In general, these are easy to use, but need to be populated in an exact,
append-only order - which, no coincidence, happens to be the order in which 
Antlr traverses a parse tree.  Populating them is taken care of for you -
they are handed to you to query, and are easy to use.

So, yes, it's weird, but it's also why this whole thing works, and can do
so faster and in a fraction of the memory footprint of most language-support
NetBeans plugins - for any language you have a grammar for.

Concepts
--------

One of the hard-won lessons of work long ago on the Java parser in NetBeans was
that _you do not keep the parse tree around once the parse is done_ - doing so
can keep arbitrarily large data structures in memory, and kills performance.  The
user is editing the code, so the syntax tree is usually changing.  The right way
to handle syntax trees is to _extract what you need to render the UI_ and
throw everything else away - you'll be doing it again, likely in a few milliseconds
anyway.

To hammer that home - in the era of NetBeans 5.0, circa 2002, we replaced the
Java editor infrastructure _entirely_ with a metadata-repository based system
that made refactoring possible - via live-editable, bidirectional syntax trees.
The performance of the result was so horrific that the entire thing got scrapped
within less than a year (replacing it with directly using `javac`'s parser,
extracting what we needed and discarding syntax trees).  Now, who, with 200+
people working full time on it, rewrites the biggest subsystem in an IDE and
then _does it again_ a year later, breaking a year's worth of everybody else's
work?  That's how bad it was.  So, seriously:  Syntax trees are for compilers.
If you're not writing a compiler, extract _only_ what you can use from the syntax
tree you are handed and get rid it.  Another will be along in
milliseconds and parsing is cheap.

Separating the concerns of "how do I get the data?" from 
"what data do I want" and "what do I want to do with it?" has other benefits:
It lets you build
tooling where changes at one layer - say, a new language version or updated
grammar - don't have cascading effects across all code that touches that
language, resulting in more maintainable, future-proof plugins.  Rename a
grammar rule, and the code that pulls stuff out of the syntax tree may
need a one-line change; but the things that use the results aren't
affected at all.

The programmatic APIs you will use are largely oriented around _extraction_,
which their names reflect.

Parsers and lexers in NetBeans use a "don't call us, we'll call you" approach -
you don't _initiate_ parsing, you _register_ something that is interested in
the parse result for a particular MIME type.  99% of the registration
code is generated by annotation processors, when using Antlr support,
and the code to adapt Antlr's lexer and parser types to NetBeans APIs for those
things is also generated.

So the main pattern here is

1. Write something that is interested in parser results for a particular MIME type
2. Get handed an `Extraction`
3. Query it, passing a key, and getting back a collection of items you described
how to extract, which have file offsets and whatever other information you asked
for them to be populated with
4. Annotate your key (a static field) with annotations that use the data collected
under that key to implement some feature, such as code folding, or navigator panels,
or semantic highlighting - often with no additional code required at all.
   * For example, to implement the Goto Declaration editor action over a key
for references to names, you literally just add `@Goto` above the field.
   * In some cases, you can optionally implement some interface and reference
your implementation from the annotation to customize the behavior - for example,
a Navigator panel is a one-line annotation, but you can implement `Appearance` to
give the items pretty icons and indenting depending on the type of source element
they represent


### Extraction

When a file is parsed, the result is an `ExtractionParseResult` - an extension
to NetBeans' parser result class, with an `extraction()` method, which returns
an type named - you guessed it - `Extraction`.


An `Extraction` is basically a typed map - pass it a key, and get back a data
structure that is (typically) a collection of region start- and end- positions
in the file (note, `end` positions in this API and NetBeans APIs are _exclusive_;
Antlr's native `stop` positions are _inclusive_!  If you directly deal in 
pulling offsets out of tokens, add 1 to the stop token's stop position!), 
with some other associated
data, such as a name and/or subtype of the thing extracted - enough information
to, say, apply a different syntax coloring to different items (even though
they may all have the same lexical token), or to create a Navigator window
panel and use different icons for different subtypes.

For example, in the Antlr Grammar Language module - the module which provides 
editor support for Antlr `.g4` files, there are three kinds of grammar rule:

 * _Parser_ rules define rules for how lexical tokens can be defined
 * _Token_ rules define lexical tokens
 * _Fragment_ rules define matchable sequences that can be reused in lexical
token rules

An `enum` named `RuleTypes` defines these three _kinds_ of named rule, and maps them to a 
`NamedRegionKey<RuleTypes>`:

```java
public static final NamedRegionKey<RuleTypes> RULE_NAMES 
        = NamedRegionKey.create("ruleNames", RuleTypes.class);
```

Code that wants to, say, populate a Navigator window or handle syntax highlighting
calls `theExtraction.namedRegions(RULE_NAMES)` and gets back a `NamedSemanticRegions<RuleTypes>`,
which is a high-performance, low-memory-footprint collection of start- and end-offsets, 
names and `RuleTypes`, which can be searched (say, for the item under the caret) or
iterated.

So, to recap:

 * Figure out what code regions that are useful to you, to implement IDE features
 * Where those regions come in several flavors you would like to render differently or 
need to distinguish, and create an `Enum` with a key for each flavor
 * Create a static method somewhere which will be called to build an `Extractor` - it will
be passed a builder which lets you define complex constraints on what parser rules or lexer
tokens should be captured for this key
```
    @ExtractionRegistration(mimeType = ANTLR_MIME_TYPE, 
                            entryPoint = YourEntryPointParserRuleContext.class)
    static void populateBuilder(ExtractorBuilder<? super GrammarFileContext> bldr) {
		...
```
 * Define as many keys (or register as many extraction building methods if things are getting complex) as you need to
capture everything you want
   * For non-nested items that occur multiple times in a file, you want `NamedRegionKey<YourEnum>`, which takes an
enum key - these are good for capturing things like Java methods or Antlr rules - things that have name and some
well-defined type information - you can retrieve a `NamedSemanticRegions` collection from the extraction that will
let you access them, find the item under the cursor, etc., highly efficiently
      * Any `NamedRegionKey` can be used to create a `NamedReferenceSetKey` which can be used to collect *references* 
to the names collected under the owning `NamedRegionKey`, and can be scoped using surrounding elements
   * For nestable or scoped items you can associate arbitrary data with, or cases where an enum is not enough,
use `RegionsKey<SomeClass>`
 * You can then either parse a document using NetBeans' `ParserManager` to access the
extraction for the current state of a document in your language, *or*, for attaching
error messages, hints and so forth, register a `ParseResultHook` using `@MimeRegistration`
to be passively called back whenever syntax highlighting or similar triggers a parse,
to decorate the parser result as you wish
 * A number of features can be built very simply and the code generated for you:
    * Annotate a `NamedReferenceSetKey` with `@Goto` and Ctrl-B - Goto Declaration - will work to
navigate in the editor to the definition of a reference
    * Annotate a `NamedRegionKey` with `@Imports` to indicate that this key's members are names of
"imported" files (you will need to implement and register at least one `RelativeResolverImplementation`
with a strategy for how to resolve a name to an actual file) to generate an `ImportFinder` implementation
which is used by Goto Declaration to open and move the cursor to the definition of some item in another file,
and used to attribute names that look like name references but cannot be resolved in this file.
     * Annotate a `NamedRegionKey` with `@ReferenceableFromImports` to auto-generate the code needed
to find that name when referenced in another file (you will need an `ImportFinder` (see previous bullet point)
registered and `RelativeResolverImplementation`)
     * Annotate a `NamedRegionKey` with `@SimpleNavigatorRegistration` to get a Navigator panel generated
for the items found by this key (you can have multiple Navigator panels, and provide an implementation of
`Appearance` to customize how they are rendered)
     * Annotate a `RegionsKey` with `@AntlrFoldsRegistration` to set up editor code-folding for regions
associated with that key
     * Annotate any key with `@HighlighterKeyRegistration` to define an additional syntax coloring of
regions found for that key (the top level `@AntlrLanguageRegistration` lets you define token-level
syntax highlighting; this lets you define semantic highlighting)
 * For complex analysis, such as scoping variables, semantic region and named semantic region collections
can be combined into a lightweight, `BitSet`-based graph which can be queried

An extraction also contains built-in collections for duplicate names (a `NamedSemanticRegions` is not
duplicate-tolerant, so duplicates are placed here), and for unknown name references (you can register
`RegisterableResolver`s for your mime type to resolve these to elements in other files), which can be
either resolved in other files, or marked as errors;  by default, editor hints are generated which
use the *levenshtein-distance* algorithm to suggest existing names for those which might be typos
and cannot be attributed.

This is the heart of the Antlr-based language support infrastructure - the glue-code to map
NetBeans and Antlr parsers, register file types and the other boilerplate tasks of language support
are generated for you.  The extraction infrastructure makes as no assumptions as possible about
what your language looks like - it simply provides ways for you to extract named regions, nested
structures and the like from a parse, and provides tools to make working with that data simple
and efficient.  So the flow is:

 1.  Something such as syntax highlighting triggers a parse
 2.  After the Antlr parse, you get a callback and can decorate the parser result, set up fixes
or anything else you want, including attaching additional data to the parse

One things to note:  As you add annotations that create features for your language, the
list of required dependencies is likely to grow - there is no safe way for an annotation
processor to add dependencies to your project, so this must be taken care of manually.

#### Keys and Collections

There are several kinds of key types and collection types obtainable from an
`Extraction` - you will learn below how to register keys and populate the
extraction after a parse.  Each of these has specific characteristics and is
right for certain kinds of data, but not others:

 1. `NamedRegionKey<Kind extends Enum<Kind>>` + `NamedSemanticRegions<Kind>` gets you
a collection of *non-overlapping, ordered* regions in the document, each of which
has a `name` and a `kind`.  These are useful for elements of a source file that
have names you would want to display, such as a list of class members (with kinds
for, say, fields, methods and inner classes) or properties in a properties file.
You can annotate a `NamedRegionKey` with `@HighlighterKeyRegistration` to add
syntax highlighting to all named regions, or `@SimpleNavigatorRegistration`
to create a navigator panel that lists the items, and can sort them naturally
or alphabetically; or annotate it with `@AntlrFoldsRegistration` to implement
editor code-folding for it.
2.  `NamedReferenceSetKey<Kind extends Enum<Kind>>` + `NamedRegionReferenceSet<Kind>` works
hand-in-hand with `NamedSemanticRegions` to let you collect references or _usages_ of
named regions.  For example, the Antlr grammar support module uses this to implement
Mark-Occurrences highlighting and Go To Source (implementing the latter is as simple
as annotating the key with `@Goto` with no arguments).
3. `RegionsKey<Kind>` + `SemanticRegions<Kind>` allows you to create a collection of regions of the file
which _may be nested_ and have any type of data (not just enums) you wish to associate with them
included in them (but please don't use Antlr trees directly).
4. `SingletonKey<Kind>` handles the special case of information that is expected
to occur _exactly once_ in a file (such as whether an Antlr grammar is a lexer grammar,
parser grammar, or combined grammar), which is used to determine what is legal
within a file or how best to present it in the UI.



### Extracting Data from a Parse

The `Extraction` gets populated as soon as the Antlr parse tree is created.  You
populate it by annotating as many static methods that take an `ExtractionBuilder`
as you want, with `@ExtractionRegistration`.  `ExtractionBuilder` has a builder-like
API for describing what work you want to do against a parse tree, without having
to write parse-tree-walking code yourself.  As much as possible, you are describing
the _recipe_ for how to extract what you want, rather than writing code to _do it_ - 
at most, you'll specify a particular type of AST node and some ancestor and other
conditions under which it should be chosen, and perhaps write a lambda or method
reference that returns some information from it.

For example:

```java
    @ExtractionRegistration(mimeType = "text/x-yasl", entryPoint = CompilationUnitContext.class)
    static void extract(ExtractorBuilder<? super CompilationUnitContext> bldr) {
        bldr.extractingRegionsUnder( COMMENTS ).whenTokenTypeMatches( COMMENT )
                .filteringTokensWith( tk -> tk.getText().indexOf( '\n' ) >= 0 )
                .usingKey( "cmt" )
                .finishRegionExtractor();
    }
```

The code above could (and in fact, the real version does) go on to describe a bunch
of other recipes associated with the same or different keys.  This code is generally
called once, to create the `Extractor` which will be run against files of this MIME
type for the remainder of the IDE session.  In this particular case, we are finding 
all _multiline_ line comments (for code folding
purposes).

Getting Started
---------------

Getting basic things working as fast and simple.  You need a working Antlr grammar
(ideally a parser or combined grammar, although you can do basic syntax highlighting 
with just a lexer grammar).

Find or create a class that you want to put the main annotation that creates
language support on.  You will anntotate it with `@AntlrLanguageRegistration`.
The basic, mostly required properties are quite simple:

```java
@AntlrLanguageRegistration(
        name = "Yasl",
        lexer = TypesLexer.class,
        mimeType = MIME_TYPE,
        file = @FileType(
                extension = "yasl", multiview = false,
                iconBase = "yasl.png", 
                hooks = YaslDataObjectHooks.class),
        parser = @ParserControl(
                    type = TypesParser.class, 
                    entryPointRule = TypesParser.RULE_compilationUnit,
                    generateSyntaxTreeNavigatorPanel = true, 
                    helper = YaslHelper.class),
        sample = YaslInfo.YASL_SAMPLE,
        lineCommentPrefix = "//",
        localizingBundle = "com.mastfrog.yasl.netbeans.antlr.Bundle",
        genericCodeCompletion = @CodeCompletion(
                ignoreTokens = {
                    S_WHITESPACE, S_SEMICOLON,
                    S_COMMA, S_CLOSE_BRACE, S_CLOSE_BRACKET, S_CLOSE_PARENS,
                    S_OPEN_BRACE, S_OPEN_BRACKET, S_OPEN_PARENS}),
```

Item by item, what this does is:

 * **name** - This is a `prefix` which will be used as the prefix for generated
classes, and if you don't supply a localization bundle, its name in the UI
 * **lexer** - this is the type of the generated Antlr lexer.  The annotation
processors will use information from the lexer to generate a large amount of
useful code, including registering a NetBeans lexer for your mime type, NetBeans
token types for all of the types in your grammar, and much more.
 * **mimeType** - Self explanatory - files are typed in NetBeans by MIME type
 * **file** - This controls the generation of `DataObject` and `DataLoader` NetBeans API subclasses
which represent individual source files (if you have a `DataObject` implementation
already, you can also provide that).  It has the following properties of note:
   * **extension** - the file extension(s) that should be recognized as this MIME type
   * **iconBase** - the icon to use - can be a fully-qualified `/` delimited path within the classpath,
or if it is in the same package as the class with the annotation, a simple file name will do
   * **hooks** - optionally, you can implement `DataObjectHooks` and provide it here, to hook
into the lifecycle of files of your type - say, to veto or do some additional work on file
deletion, copy or rename.
 * **parser** - this provides enough information to generate a NetBeans parser that wraps
the Antlr generated parser.  It has the following properties of note:
   * **type** - the generated parser class
   * **entryPointRule** - this is whatever the outermost rule in your grammar is - the rule
that represents an entire source file or compilation unit.  By convention it is the first rule
in the grammar, but nothing enforces this.  Several other types, including extraction registrations,
will need to match this type to be run.
   * **generateSyntaxTreeNavigatorPanel** - For debugging language support, this auto-generates
a Navigator panel that simply shows the syntax tree of the file being edited.  This is likely not
interesting to _users_ of your plugin, but can be invaluable as a quick debugging tool when writing
a plugin.
   * **helper** - A subtype of `ParserHelper` which you implement.  If you want to hook directly
into the post-parse cycle every time - which is particularly useful for adding error messages to
the file, or hook into lexer and parser creation, this is the place.
 * **sample** - Sample code to use in the Fonts and Colors page of the Tools | Options, when the
user wants to change fonts and colors for your language
 * **lineCommentPrefix** - If set, Ctrl-/ or your standard binding for the Comment/Uncomment action
will work
 * **localizingBundle** - Allows you to localize the name of your language, token types and similar
(see below)
 * **genericCodeCompletion** - It is possible, using only internal parser information, to do
basic keyword completion.  How useful this is depends on how much the language relies on predefined
keywords, as opposed to names defined in source files.  The `ignoreTokens` lets you turn off
completion of things that nobody really wants code completion for, like single-character things
such as end-of-line semicolons or math operators where you're likely to know what you want and
pressing a key is less effort than code-completion.

The rest of the annotation is going to consist of _token categories_ - the categories used for
syntax highlighting.  These can get a bit large, if your grammar has a lot of tokens or rules.
The `categories` annotation array consists of a list of _token categories_ - the categories
you see in the Tools | Options window and can set color and font settings on.  You will group
token ids into different token categories, and then associate one or more background-color / foreground-color
/ font-style, decoration and decoration-color with the category.  Each coloration can be tied
to one or more _editor themes_ - it is good to always cover `NetBeans` (the default theme) and
a few of the others that ship with the IDE.  In particular, it is usually necessary to have
at least one set of colorings for light themes and one for dark themes.

It looks like this:

```java
categories =            
    @TokenCategory(name = "identifier",
            tokenIds = {
                ID, POSSIBLE_TYPE_NAME, NESTED_TYPE_NAME
            },
            colors = {
                @Coloration(themes = {"NetBeans", "NetBeans55"}, derivedFrom = "identifier",
                fg = {67, 67, 156}, bold = true),
                @Coloration(themes = {"NetBeans_Solarized_Dark", "BlueTheme"}, derivedFrom = "identifier",
                fg = {40, 40, 160}, bold = true)
            }),
    @TokenCategory(name = "operator",
            tokenIds = {
                S_CLOSE_BRACE, S_OPEN_BRACE, S_OPEN_BRACKET, S_OPEN_HINTS,
                S_CLOSE_HINTS, S_CLOSE_PARENS, S_OPEN_PARENS, S_CLOSE_BRACKET,},
            colors = {
                @Coloration(themes = {"NetBeans", "NetBeans55"}, derivedFrom = "operator",
                fg = {178, 130, 100}),
                @Coloration(themes = {"NetBeans_Solarized_Dark", "BlueTheme"}, derivedFrom = "operator",
                fg = {40, 40, 160})
            }),
            ...
}
```

Colors may be specified as either 3-element RGB or 4-element RGBA `int` arrays.

There are usually forms of semantic highlighting that cannot be done just with lexer
tokens; for those, usually you will use an extraction key and `@HighlighterKeyRegistration` - but
the `@TokenCategory` annotation _does_ allow for simple "highlight everything in this rule like this"
highlighting by specifying, e.g. `parserRuleIds={MyParser.RULE_one, MyParser.RULE_two}`.

Bear in mind that highlighting has a *layering* order (which can be specified in `@HighlighterKeyRegistration`),
so two superimposed highlightings that both set the background will not be combined (even if one has alpha),
but the higher overrides the lower.  In addition, there are layers of sets-of-layers in highlighting, 
specifying the syntax "rack" a highlighting belongs to - this enables things like mark-occurrences
to always override syntax values, even though the code for each doesn't know about the other.

Simply minimally defining the `@AntlrLanguageRegistration` with a lexer and some token categories
and other required fields is sufficient to get basic language suport up and running.


### Errors in Sources

Syntax errors - errors that make the code or a portion of unparseable - will get automatically
flagged and highlighted in the editor.  For other kinds of errors, use the `Fixes` and 
`ParseResultContents` classes, an
instance of which will be passed into your `NbParserHelper`'s 
`onParseCompleted(YourParseTreeRoot tree, Extraction extraction, ParseResultContents populate, 
Fixes fixes, BooleanSupplier cancelled)` method.


### Relative resolution

Most non-trivial languages have some concept of `imports` - the ability to reference contents
that live in another file that can be located using some combination of indicators in the
file's contents and its location on disk.

Antlr support abstracts the combination of a file or other input stream source, a mime type,
import resolution,
and the ability to create an Antlr `CharStream` (used by lexers) into the type
`GrammarSource` (designed to be usable in any IDE, not just NetBeans).  The API for
`GrammarSource` includes a registry of converters, so you can, with the same results,
call `GrammarSource.find(T fileOrDoc, String mimeType)` with a NetBeans `FileObject`,
a `java.nio.file.Path`, a `java.io.File` or a `javax.swing.text.Document` and get an
equivalent result.

Import resolution involves two steps:

 1. Implement and register a `RelativeResolverImplementation` (can be parameterized on
`FileObject` or `File` or `Path` and the framework will do the necessary conversions to
retrieve what was requested).  Note that a `GrammarSource` over a live `Document` will
be that _document's_ content, which may have been edited from what exists on disk -
this enables live parsing of grammars as they are being edited (with a bit of hacking
of Antlr's internal plumbing).

```java
@ServiceProvider(service=RelativeResolverImplementation.class, path="antlr-languages/relative-resolvers/text/x-g4")
public class AntlrFileObjectRelativeResolver extends RelativeResolverImplementation<FileObject> {
    public AntlrFileObjectRelativeResolver() {
        super(FileObject.class);
    }
    @Override
    public Optional<FileObject> resolve(FileObject relativeTo, String name) {
        return ProjectHelper.resolveRelativeGrammar(relativeTo, name);
    }
}
```

2. Implement `UnknownNameReferenceResolver<GrammarSource<?>, NamedSemanticRegions<YourKeyType>, NamedSemanticRegion<YourKeyType>, YourKeyType>`
and pass that to `Extractor.resolveExtraction()` or `Extractor.resolveUnknowns()`.  If your `Extraction` uses any
`NamedRegionKey`s that have associated `NamedReferenceSetKey`s, then any names that could not be
resolved within the file are available there.

### Code Formatting

The `antlr-formatting` module provides code formatting support.  Similar to how
extractions are built, you implement (typically) `StubFormatter` and annotate it
with `@AntlrFormatterRegistration`, and get passed some builder-like objects
that let you define formatting and analysis rules.

`StubFormatter` has a method you implement for that purpose:
`configure(LexingStateBuilder<StateEnum, ?> stateBuilder, FormattingRules rules, C config)`.

The `FormattingRules` is what lets you define rules - preferring token-, not parser-rule-
based (if you've used an editor that completely screwed a source file because you
reformatted a source file that had an error in it, that is why - you *can* rely
on parser rules, but you shouldn't).

The `LexingStateBuilder` - like extractions - lets you define what you're interested
in _tracking_.  You provide your own custom `Enum` you will use as keys to look up metrics,
and then describe what to track to it.  Formatting rules are often aesthetic and
don't lend themselves to one-size-fits-all thinking if you want to actually like
the results of your formatter.  So, if you want to, say, have a formatting rule
that is only active when the token being formatted has, say, a close-parentheses
token not followed by a semicolon within two tokens to the right of it, you
can tell your `LexingStateBuilder` that you want to track the relative position
of semicolons, only when you're within a particular parser rule (or, better, only when you
have passed some opening delimiter but not passed a corresponding closing one),
it makes that sort of thing easy to do.  `LexingStateBuilder` supports counting
tokens that match conditions you supply, pushing integer values in a stack-like
structure, setting and unsetting things, and more.

The `simple-test-language` project has tests in the `antlr-formatters` project
that offer some good examples.  For example, here we simply track the current token's depth
within nested braces, to provide different newline behavior based on depth.

```java
stateBuilder.increment(BRACE_DEPTH)
        .onTokenType(S_OPEN_BRACE)
        .decrementingWhenTokenEncountered(S_CLOSE_BRACE);
```

This kind of thing is useful, for example, in Java, if you like spaces inside
your innermost parentheses, but not if they have no contents and not 
surrounding ones, to take

```java
doSomething(foo(thing1,thing2(),bar()));
```

and easily format it as

```java
doSomething( foo( thing1, thing2(), bar() ));
```

if you want, rather than `doSomething( foo( thing1, thing2( ), bar( ) ) );` - 
the space to see where the arguments are delimited is useful, while the
others are gratuitous.

Defining formatting rules is fairly straightforward.  You define and add a new
`FormattingRule` by calling one of the methods on the `FormattingRules` you
are passed, such as `onTokenType(int... tokenIds)`, passing constants from
your Antlr lexer.

You conclude a rule by supplying the `FormattingAction` to call if the
tests on the rule pass (and these can include whether a value from the
`LexingState` is present, absent or greater than, less than or equal to
some value - and what the `LexingState` stores is wildly flexible.  The
framework provides a set of standard formatting actions (prepending/appending 
spaces, newlines, indenting - and reflowing text to a line length limit
and a collation);  formatting actions can be composed together.

So defining a simple rule looks like this:

```java
            rules.onTokenType(ID, QUALIFIED_ID)
                    .wherePrevTokenType(K_IMPORT, K_NAMESPACE, K_TYPE)
                    .format(PREPEND_SPACE);
```

Typically, you will have some predefined actions you will resue in a bunch
of places, like indenting to some nesting depth, and then use those
from multiple rules:

```java
    FormattingAction indentCurrent = PREPEND_NEWLINE_AND_INDENT
            .by(BRACE_DEPTH)
            .wrappingLines(maxLineLength, doubleIndentForWrappedLines);
```

Here is a more advanced taste of some of the things `LexingState` can
do, from the formatter for Antlr grammar files:

```java
    lexingStateBuilder
    // record the position of the nearest colon to the left, keeping it
    // until another colon is encountered
    .recordPosition(AntlrCounters.COLON_POSITION)
    .onTokenType(COLON)
    .clearingOnTokenType(-1)
    // Record position of the most recent left brace,
    // keeping a stack of them so we pop our way out,
    // for indenting nested braces
    .pushPosition(AntlrCounters.LEFT_BRACE_POSITION)
    .onTokenType(LBRACE, BEGIN_ACTION)
    .poppingOnTokenType(RBRACE, END_ACTION)
    // Count the number of ;'s in actions and header blocks to see
    // if they be formatted readably as a single line, e.g. { foo(); }
    .count(AntlrCounters.SEMICOLON_COUNT).onEntering(LBRACE, BEGIN_ACTION)
    .countTokensMatching(anyOf(VOCABULARY, SEMI, ACTION_CONTENT))
    .scanningForwardUntil(anyOf(VOCABULARY, END_ACTION, RBRACE))
    // Increment a counter for nested left braces
    .increment(AntlrCounters.LEFT_BRACES_PASSED).onTokenType(LBRACE, BEGIN_ACTION)
        .decrementingWhenTokenEncountered(RBRACE, END_ACTION)
```

and some rules that use them:
```java
        rules.onTokenType(ACTION_CONTENT)
                .wherePreviousTokenType(ACTION_CONTENT)
                .whereMode(MODE_ACTION)
                .format(FormattingAction.EMPTY);
        rules.onTokenType(BEGIN_ACTION).whereMode(MODE_ACTION)
                .wherePrevTokenType(ID, TOKEN_ID, FRAGDEC_ID, PARSER_RULE_ID)
                .named("action.spaces.a")
                .whereNextTokenType(ACTION_CONTENT)
                .when(AntlrCounters.SEMICOLON_COUNT).isGreaterThan(1)
                .when(AntlrCounters.LEFT_BRACES_PASSED).isLessThanOrEqualTo(1)
                .format(PREPEND_SPACE.and(APPEND_NEWLINE_AND_DOUBLE_INDENT.by(AntlrCounters.LEFT_BRACE_POSITION)));
        rules.onTokenType(BEGIN_ACTION).whereMode(MODE_ACTION)
                .named("action.spaces.b")
                .priority(1)
                .wherePrevTokenType(ID, TOKEN_ID, FRAGDEC_ID, PARSER_RULE_ID)
                .whereNextTokenType(ACTION_CONTENT)
                .when(AntlrCounters.SEMICOLON_COUNT).isLessThanOrEqualTo(1)
                .when(AntlrCounters.LEFT_BRACES_PASSED).isLessThanOrEqualTo(1)
                .format(PREPEND_SPACE);
```

The final result of configuring your formatting rules is effectively a giant
tree of predicates with formatting actions as the leaf nodes.  While these do
make use of `java.util.function.Predicate`, all of the built-in predicates
and ones you compose are implemented to have a meaningful `toString()` implementation,
and there is support for conditionally turning on logging - so it is possible
to usefully print out what rules the system has and which ones it is running
on specific tokens.  For further differentiation, the `named()` method simply
adds a logging-friendly name to a particular rule, to make it easier to map
similar logged rules back to the exact code that created them.

#### Formatting Rule Precedence

By default, every formatting rule is effectively tested against every token
(though in practice this can be optimized a bit).  Rules are first sorted
in _order of specificity_ - that is, there are a lot of ways to make a rule
more specific - every method call you see named `where*` or `when*` is one
such.  So, if two rules could match a token, and one is more specific than
the other, the more specific one will win, and its action will be the one
that runs for that token - for each token, only one formatting action
will be run, ever.


### Code Completion

Code completion follows a very similar pattern to formatting - you are passed
a builder, define conditions (tokens, adjacent tokens) under which completion
items may be available, and pass a callback to collect those items which 
should be offered to the user as completions.

This API is still under development, so for now, just an example of working
code:

```java
public class NamesCompletions implements CompletionItemProvider<String> {

    @MimeRegistration(mimeType = YaslInfo.MIME_TYPE, service = CompletionProvider.class)
    public static CompletionProvider get() {
        NamesCompletions c = new NamesCompletions();
        return AntlrCompletionProvider.builder( NamesCompletions::createLexer )
                .<String>add()
                .whenPrecedingTokensMatch( "x")
                .whereCaretTokenMatches( S_OPEN_PARENS )
                .withInsertAction( InsertAction.REPLACE_NEXT_TOKEN )
                .withDeletionPolicy( DeletionPolicy.DELETE_NEXT_TOKEN, DeletionPolicy.DELETE_TOKEN_AFTER_NEXT)
                .andSubsequentTokens( POSSIBLE_TYPE_NAME, S_CLOSE_PARENS)


                .whenPrecedingTokensMatch( PARTIAL_NAME, S_OPEN_PARENS, POSSIBLE_TYPE_NAME )
                .whereCaretTokenMatches( S_CLOSE_PARENS)
                .withInsertAction( InsertAction.REPLACE_CURRENT_TOKEN).withDeletionPolicy( 
                    DeletionPolicy.DELETE_TOKEN_BEFORE_PREVIOUS, DeletionPolicy.DELETE_PRECEDING_TOKEN, 
                    DeletionPolicy.DELETE_CURRENT_TOKEN)

                .whenPrecedingTokensMatch( PARTIAL_NAME, S_OPEN_PARENS, NESTED_TYPE_NAME )
                .whereCaretTokenMatches( S_CLOSE_PARENS)
                .withInsertAction( InsertAction.REPLACE_CURRENT_TOKEN).withDeletionPolicy( 
                    DeletionPolicy.DELETE_TOKEN_BEFORE_PREVIOUS, 
                    DeletionPolicy.DELETE_PRECEDING_TOKEN, 
                    DeletionPolicy.DELETE_CURRENT_TOKEN)

                .whenPrecedingTokensMatch( PARTIAL_NAME, S_OPEN_PARENS)
                .whereCaretTokenMatches( POSSIBLE_TYPE_NAME )
                .withInsertAction( InsertAction.REPLACE_CURRENT_TOKEN).withDeletionPolicy( 
                    DeletionPolicy.DELETE_PRECEDING_TOKEN, 
                    DeletionPolicy.DELETE_NEXT_TOKEN)
                .andSubsequentTokens( S_CLOSE_PARENS )


                .whenPrecedingTokensMatch( COLON, S_COLON ).whereCaretTokenMatches( S_WHITESPACE)
                .whenPrecedingTokensMatch( PARENS, S_OPEN_PARENS ).whereCaretTokenMatches( S_WHITESPACE )
                .withDeletionPolicy( DeletionPolicy.DELETE_PRECEDING_TOKEN)

                .ignoring( S_WHITESPACE, COMMENT, LINE_COMMENT )
                .stringifyWith( NAME_PATTERN ).forKind( StringKind.DISPLAY_NAME )
                .withPattern( PREFIX_PATTERN ).withMessageFormat( "in {0}" )
                .forKind( StringKind.DISPLAY_DIFFERENTIATOR )
                .withPattern( ALL_PATTERN ).withMessageFormat( "({0})" ).forKind( StringKind.TEXT_TO_INSERT )
                .withPattern( ALL_PATTERN ).forKind( StringKind.INSERT_PREFIX )
                .build()
                .setSorter( NamesCompletions::dotCount )
                .build( c ).build();
    }
 ```


### Extraction, hashing and caching extractions

`Extraction` and its related classes can be serialized using plain Java 
serialization (however fraught that is), making it possible to cache on
disk data about a file which, if the serialized `Extraction`'s file date is
newer, can be deserialized as a valid representation of the file.

That, however, creates the issue of deserializing an extraction which
was created by a version of the module which did different things.

As such, `Extractor` implements `Hashable` - an interface which allows for
computing a hash of all of the items (including callbacks!  Use static
method references or real class implementations, not lambdas for consistent
hashing!) which can be compared as an opaque string with the hash of the
current `Extractor` - if they match, then the serialized extraction was
created from an `Extractor` identically configured.  If not, all bets are
off if you try to load it.

The simple way to manage this for your language is to name the cache
subfolder where you keep all historical extractions with the hash of
the extractor, so there will simply not be anything there if a newer version
of your module is looking for a cached extraction for some type.

But note that, thanks to the wonders of using custom, int-array-based
data structures, event complex extractions are very fast, and on small
files, are likely to be faster than the IO required to load a serialized
extraction.


### JFS

JFS is a piece of more advanced tooling, which allows you to generate
source files from an Antlr grammar to an in-memory JavaFileSystem
(the virtual filesystem `javac` uses internally),
run `javac` on them and even load the generated classes in an isolated
classloader, and parse a file.  The backing storage for JFS can either
be the Java heap or a memory mapped file (under the hood it uses a simple
but workable block allocation map which can be wiped for reuse easily);
live JFS filesystems install a URL handler so a JFS file can be read via
its URL.  JFS also supports mapping Document instances as files, so a
"file" processed by Javac can be backed by the contents of a live editor.

On a warmed up 8Gb i7 laptop, doing all of the above for a simple grammar
can be done in 35 milliseconds - fast enough to do on every keystroke without
the user detecting slowdown.  This makes possible the live-preview functions
that let you have a split editor with sample code in the language you're
editing the grammar for, with some simple syntax highlighting auto-generated
or configured, and edit the grammar and instantly see the effects of your
changes.

