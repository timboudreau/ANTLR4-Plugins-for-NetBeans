Antlr Grammar Editing Plugins for NetBeans
==========================================

In this directory are general-purpose tooling for generating most of
a language plugin for NetBeans off of an Antlr Grammar and a few annotations.
In the spirit of eating one's own dog food, under this project are modules which
use that tooling to implement support for editing Antlr grammars themselves.

Those that are working reliably are depended on by the module `antlr-editing-kit`.

Specifically, what's here:

 * General purpose Antlr editing, syntax coloring, hints, etc.
 * Support for generating a grammar into Java code in an in-memory filesystem,
compiling it there, and running the resulting grammar against text input, to
provide: 
     * Grammar error highlighting that calls Antlr itself (as opposed to recreating all
of its grammar validation code in a plugin) in real-time
     * Ability to associate a file extension with a grammar file
     * Live editable syntax highlighting of files in your grammar, which are updated
as you edit the grammar


Architecture
============

### JFS

The heart of much of the Antlr tooling is **JFS**, an implementation of `javac`'s 
[`JavaFileManager`](https://docs.oracle.com/javase/9/docs/api/index.html?javax/tools/JavaFileManager.html)
virtual filesystem interface with a few twists:

 * The backing storage for files is one of several back-ends (the default heap-based
   back-end implementation is the stable; off-heap storage using direct `ByteBuffer`s
   or a memory-mapped file are also available, but are not 100% corner-case-free yet)
 * Disk-files and Swing documents can be mapped ("masqueraded" - since "map" is a loaded
   term in the filesystem world) into the filesystem namespace,
   and are indistinguishable from "real" JFS virtual files to anything reading them
 * File modifications can be reliably tracked in several ways (_including_ modifications
   to masqueraded documents):
    * Create a `Checkpoint` with `jfs.newCheckpoint()` before some operation that will write files, and collect those
      files that were modified from it (discarding it) once that operation is complete
    * A `JFSFileModifications` can be created from the output of a Checkpoint, or
      from the JFS itself, and set to filter the set of locations and files it is interested in.
      Call <code>changes()</code> on it to collect the set of additions/deletions/modifications
      that have occurred since creation or the last call to <code>refresh()</code>.
      `JFSFileModifications` uses modification timestamps to filter the initial list,
      then compares SHA-1 hashes to accurately determine modification state.
 * Builders for running a `javac` compilation job against a the contents of a `JFS`
 * Expands on the basic `ClassLoader` support that is part of `JavaFileManager` to include
   expanded support for
    * Creating isolated classloaders that can be used and thrown away (be careful not to
      leak `Class` objects - anything constructed inside one and passed back out should
      either consist of JDK classes or classes loaded intentionally from the parent classloader)
    * Creating classloaders that allow loading a constrained subset of classes available
      from the caller's classloader, so things that run against compiled Antlr grammar
      files can return complex analysis objects without needing proxies or other
      magic to avoid either leaking types or having them be opaque to module code.

### MemoryTool

On top of JFS sits **MemoryTool**, a subclass of ANTLR 4's 
[`Tool`](https://www.antlr.org/api/JavaTool/index.html?org/antlr/v4/Tool.html), the
engine that generates grammar sources from grammar files.  `MemoryTool` subclasses
`Tool` and a few other classes to enable attempts at reading files to be translated
into reading from and writing to a `JFS` instance instead.

Largely the code of `MemoryTool` is simply alternate filesystem management and error
message collection and analysis (things like ensuring all errors contain accurate
file position offsets, not just lines and line numbers).  It includes a few improvements
over the default Antlr error reporting that are best implemented inside the tool:

 * Epsilon analysis - by default Antlr just tells you _some rule in here calls some other chain
   of rules, and somewhere in there is something that can match the empty string_ and
   leaves the user to spend hours figuring out what;  `MemoryTool` will actually
   identify the root cause
 * Left-recursion - Antlr supports self-left-recursion, but not indirect left-recursion -
   in other words, `foo : foo LeftParen someRule RightParen;` is legal, but
   `foo : prefix* foo LeftParen someRule RightParen;` is not, and neither is
   `foo : bar LeftParen someRule RightParen; bar : prefix* foo (',' foo)*`.  This
   can occur across a deep chain of rules, making tracking down the culprit
   recusion path(s) quite difficult.  `MemoryTool` locates and provides the exact
   locations and reasons for indirect left-recursion so they can be highlighted and
   quickly fixed.
 * AST-elision - certain constructs are legal in Antlr grammars, but either dangerous
   or difficult to detect and handle.  For example, some Antlr 4 grammars include
   legacy Antlr 3 `returns` clauses which return the user's type for some portion of
   the sytax tree (which would mean mapping unknown amounts of stuff into the JFS
   namespace and/or compiling it, and depending on the state of the user's project, might not have
   associated `.class` files to use).  For analysis purposes, where we just want to
   extract a proxy for the syntax tree without actually letting `ParseTree` objects
   escape the classloader, we can simply remove the `returns` nodes from the grammar's
   AST entirely before generating Java sources in-memory, and get a usable grammar.
 * Abiility to (with compatibility caveats), use the version of Antlr on the user's
   classpath to build and run their grammar, rather than the version the module
   uses (this is not currently done, but was tested in an early prototype and works)
 * MemoryTool tracks which file is it processing, and what other files are opened
   during that process, recursively, so that it can build a lightweight 
   dependency-graph of which files depend on which others, for use when determining
   if a change in a dependent file should trigger an update of a depender file

One thing not supported at present is grammar `members` clauses that reference classes
from the user's project classpath.  As with the reasons for AST-elision, this is possible
(get the project's ClassPathProvider and add it to the JFS classpath), but dangerous
as the user's project may not currently be built, or even compilable, and analysis
should succeed even in that case.

Grammars that contain code, semantic predicates, etc. that do not reference classes
other than those of ANTLR and the JDK will run as-written.


### Plumbing That Ties Antlr Parsing, JFS and MemoryTool Together

The remainder of the projects here are largely pieces that connect the above,
plus garden-variety Antlr editing features:

`antlr-file-support` uses the Annotation-based API to generate a NetBeans lexer,
 parser, file type, etc. for Antlr grammar files, using its own Antlr 4 parser for
 Antlr grammar files.  Plugins that generate hints, error highlighting, and the
live-Antlr-preview all hang off of that support, which generates events and an 
`Extraction` (see [the README below this one](../README.md) containing an extensible
set of structural elements of the parse tree, whenever the grammar file is reparsed.
So the root event that nearly everything hangs off of is _the grammar file gets
reparsed because of an edit.

On top of that, the design is _subscription-based_ - and there are several nested
layers of things to subscribe to that build on each other.  The pattern for the way
the code is laid out is that these come in pairs _module-which-implements-the-mechanics-of-X_
paired with _module-that-lets-you-to-subscribe-to-X-being-done-for-one-file/project/whatever_ -
and in most cases, for each task there is triplet of classes, _builder-of-job-to-do-X_,
_doer-of-X_, _result_of_doing_x_.

For example, `antlr-in-memory-build` implements 
`MemoryTool` and `AntlrGeneratorBuilder`, `AntlrGenerator` and `AntlrGenerationResult`.
The `antlr-live` module provides an entry-point, `RebuildSubscriptions` that lets you
_subscribe_ to the regeneration of Java sources from a grammar (and manages a
JFS for one project, and masquerades files and/or documents [transparently switching
to document-mapping mode when a file is opened in the editor, and back to file-masquerading mode
if the document is closed] into that JFS, and updates the mapping appropriately on 
move/delete/rename events in the physical file system).  So when the first caller
subscribes to regeneration events for a grammar file, a JFS is created for that
project, and a callback is subscribed to reparses of that file, which will run
Antlr generation (synchronously or asynchronously - see `ActivityPriority` - certain
tasks like regenerating modified grammar sources for a lexer over that grammar's
language that needs accurate tokens _right now- simply must run synchronously),
and forces an initial generation event.

`antlr-live-execution` defines a generic SPI (service-provider interface) for plugging
in things that would like to be called whenever the generated grammar source files
have been recompiled, and attaches a subscriber to `RebuildSubscriptions` which will
perform compilation (if needed) and call the SPI implementations, let them generate
some analysis (or whatever) code into the JFS and configure a classloader the generated
code should be run with.

`antlr-live-parsing` is one concrete implementation using that SPI (there are others
in the tests), to provide an `EmbeddedAntlrParser`, which can be passed a `CharSequence`,
enter an isolating JFS classloader, run its generated analysis class against the
lexer and parse tree from the grammar, and return complete information about the
resulting syntax tree, such that coloring, error highlighting and more can be done
in the IDE without directly referencing any types from the grammar.  When the
grammar changes, the callback it registered will transparently update the analyzer
code if needed, and replace the classloader and information about up-to-dateness
that the embedded parser uses to work its magic.

Callers can also susbcribe to updates of the environment of `EmbeddedAntlrParser`
itself, so text can be re-parsed and re-highlighted when the grammar is edited.

So, opening the preview window of a grammar file results in a fairly complex
set of events:

 * A synthetic, recognizable MIME type is invented and cached to disk on first
open (NetBeans editor-support is entirely based on registering services by MIME type)
 * An `EmbeddedAntlrParser` is requested for that language, triggering registration
of the chain of callbacks described above back to grammar-reparse events, and
is held by the NetBeans `LanguageHierarchy` instance
 * The file (or empty text to collect token information) is parsed at least 
once, generating the set of the tokens the on-the-fly-created NetBeans `Lexer`
will use (this will be updated on reparses - which, since NetBeans' editor 
infrastructure was not written with languages being replaced on-the-fly,
isn't pretty but works reliably)
 * Highlighters, error-hint generators and similar also subscribe to updates
of the grammar or its output, and get their state updated on changes

And typing a single character in an Antlr grammar file with the preview window
open results in a fairly complex sequence of events:
 * The grammar java sources in the JFS are regenerated (updating error and 
   warning hints as a side effect)
 * If generation succeeds, those sources are recompiled into the JFS's `CLASS_OUTPUT` are
 * If that succeeds, the analyzer (and anything else registering an `InvocationRunner`)
   regenerates its code into the JFS and compiles it
 * If that succeeds, the `EmbeddedAntlrParser` gets its environment updated
   with a new `GrammarRunResult<EmbeddedParser>` that contains the thing to call to actually
   do a parse (basically just a wrapper that sets the classloader and invokes a
   method reflectively on the generated class with the text to parse)
 * If that succeeds, then any listeners on the parser are notified that they
   might want to take the text they last passed in for highlight, error collection
   or whatever, and do that again now that the grammar has changed

Most of the code and complexity in this tightly chorographed dance is solving
the problem of _deciding when to do nothing_, since some of this work is expensive
enough that, while you can do it frequently, you don't want to do it on every
keystroke.  The knotty parts of this are:

 * Swing documents always end with a synthetic newline;  NetBeans' Parser API's
   `Snapshot`s do not, and it is possible to be called with either, for the same
   document, depending on what is listening and how - so if we are asked to parse
   identical text, plus or minus a newline, since there's no need to incur the
   expense of reparsing for that

 * JFS's are per-project, so the same JFS has a single `PerProjectJFSMappingManager`
   which maps all of the Antlr files in that project into the JFS's namespace.
   A generate/recompile triggered by some file in the project will usually regenerate
   and build java sources for a number of related files.  If the lexer was already
   rebuilt because a parser that requires it was, then things interested in
   regenerate/rebuild events on the lexer should not force their own regenerate/recompile
   cycle, they should notice that their files are already up-to-date with the
   grammar file and use them if present.  Conversely, if a dependency of a
   grammar file currently being edited is modified, that _should_ trigger a rebuild
   of that other grammar file - if the set of lexer tokens changes out from under
   a parser, all highlighting of the parser and things that use code generated
   from it should also get updated - but not more than once.

 * Frequently a change has no effect on the grammar - changes in whitespace or
   comments should not trigger a reparse.  `Extraction` automatically generates
   a hash of token ids and token contents for _those tokens on channel 0_ of
   any Antlr grammar - since the common practice for most Antlr languages is
   to route comments and whitespace to other [channels](http://meri-stuff.blogspot.com/2012/09/tackling-comments-in-antlr-compiler.html)
   that are invisible to the parser, while by default channel _0_ is the one
   of interest (see [`CommonTokenStream`](https://www.antlr.org/api/Java/org/antlr/v4/runtime/CommonTokenStream.html))
   to the parser.  So by comparing previous and current token hashes, we can
   determine if, yes, there was a change in the document, but it was not a semantic
   change that could affect parsing, and ignore it.

 * A `GrammarGenerationResult` may be up-to-date with itself, and a
   `CompileResult` of its files may be up-to-date with itself, but the `CompileResult`
   may be older than the generation result, so that needs to be detected.


Projects
========

The work here is divided up into a number of orthagonal bits of functionality, and
since the functionality is rather complex, layered into separate modules to provide
sanity and organization.

The building and running of Antlr grammars in-memory is the lion's share of the
code, and relies on the `jfs` in-memory javac filesystems project in the directory below to work its magic.
That functionality is then divided up into:

 * `antlr-project-helpers` - An API that allows for looking up Antlr source folders
and configuration for a project or relative to a file
 * `antlr-project-helpers-maven` - Implements the SPI from `antlr-project-helpers` to
correctly find Antlr folders, read and update Antlr configuration for Maven projects
 * `antlr-in-memory-build` - Allows code to map live grammar documents into JFS
and run Antlr against them, generating collecting a result object with any errors
or output, and retrieving the resulting grammar.  This project includes the extended
version of Antlr's `Tool` which can read and write from JFS.
 * `antlr-live` - Builds on `antlr-in-memory-build` providing an API to subscribe to
rebuilds of a particular Antlr grammar file or its dependencies, which can be automatically
run when the file or document is edited
 * `antlr-in-memory-execution` - Can take a JFS an Antlr grammar has had code generated into,
compile it and run code that calls it in an isolating classloader (potentially with the
version of Antlr the user has set for the project rather than the built-in version)
 * `antlr-live-execution` - Builds on `antlr-in-memory-execution` to provide an API
to subscribe to updates to a grammar file or its dependencies, and generate, recompile and 
rerun some provided code that calls the generated classes
 * `antlr-live-parsing` - Builds on `antlr-live-execution` to provide standardized
analysis code which can be passed some text, parse it with a generated Antlr grammar in
an isolated classloader, and then throw a parse tree and grammar details back over 
the wall which does not reference any classes or objects that could have been loaded
by the isolating classloader, so each parse does not leak memory
 * `adhoc-mime-types` - Generates and stores legal (if weird) MIME types which can be
resolved to an Antlr grammar file, allowing all of the NetBeans MIME-typed based
editor registration mechanisms to work against languages discovered at runtime which
belong to Antlr grammar files
 * `antlr-live-editors` - NetBeans editor support for ad-hoc file types based from
Antlr grammars, syntax coloring and more, and actions to associate a grammar file with
a particular file extension and register all of the plumbing needed to simulate a
language plugin for that grammar's language.  This necessarily replicates a lot of
the declarative registration mechanisms NetBeans uses internally, manually, registering
its own MIMEResolvers, MIMEDataProviders, EditorKits and more.
 * `antlr-live-preview` - Builds on `antlr-live-editors` to generate on-the-fly
sample files a user can edit, based on an Antlr grammar, and registers a multi-view
tab next to the Antlr source tab which provides a split view of both, updated on the
fly, and controls for customizing syntax highlighting


