# NetBeans Antlr Support

This project contains two sets of Antlr-related NetBeans plugins:

 * Tooling to make it easy to *write* Netbeans language-support plugins for languages with Antlr grammars
    * Most common language features and glue code are generated for you from annotations - syntax coloring, code completion, navigator panels
    * These can be extended further as you would with any language, and there are APIs that make that easy - for example, code formatting
    * The [API docs](./api.md) describe how to develop language plugins further
 * A plugin for developing Antlr grammars including
    * Syntax highlighting
    * Navigator panels
    * Code completion
    * Formatting
    * *Live-preview* - syntax highlighting and error checking of code in the language you are developing, which is updated as you edit the grammar, so you can see the effects of your changes instantly

## Projects

The following is the project structure.  Many of these plugins define APIs, but typically only generated
code will use them:

 * **antlr-code-completion** - API for writing code completion, with support for generic keyword-completion using Antlr's internals
 * **antlr-code-folding** - API for writing code folding
 * **antlr-common** - Common icons and other things used by the GUI
 * **antlr-formatters** - Flexible API for writing code formatters based on Antlr grammars
 * **antlr-highlighting** - Syntax highlighters which work with extractions
 * **antlr-input** - API for mediating the myriad types used to represent files and documents and to look up related files, and to generate
an appropriate Antlr `CharStream` to pass to a Lexer no matter what file representation is being used
 * **antlr-input-nb** - Implements converters and similar for NetBeans types (FileObject, DataObject, Source, Snapshot, Path, File, Document)
so that these may be used interchangably for parsing and lexing.  If you don't seem to be able to parse anything, this is probably missing.
 * **antlr-language-spi** - Provides the SPI for integrating Antlr languages, most annotations and the glue-code for wrapping an Antlr Lexer
in a NetBeans Lexer, and Antlr Parser in a NetBeans Parser, and the plumbing that makes parsing work
 * **antlr-navigators** - API for writing Navigator panels against extractions
 * **antlr-suite** - A test application, with some scripting to build a NetBeans application structure including all of the modules
here, since `nbm-maven-plugin` does not make that easy.  Build and then run the `runone` script in the project root to test changes.
 * **antlr-wrapper** - Wrapper module for the Antlr runtime and tool
 * **extraction** - API for registering and building extractions
 * **extraction-data-models** - The high-performance, low-footprint data models that Extraction uses
 * **jfs** - An in-memory (or mapped file) implementation of javac's java file system, allowing grammars to be compiled and
executed against entirely in-memory, used by the live preview functionality of the Antlr language plugin, which can have
documents or files mapped into it, and easily create isolated classloaders over it
 * **jfs-nb** - Implementation of JFSUtilities SPI to allow JFS to use NetBeans calls to determine character encoding,
document timestamps and a few other things
 * **mastfrog-utils-wrapper** - Wraps a few libraries by the same author as these plugins
 * **misc-utils** - Utility classes
 * **protoc-antlr** - A demo protobuf plugin
 * **registration-annotation-processors** - These are the annotation processors that do the heavy lifting of the large
amount of code generation that makes the magic happen
 * **simple-test-language** - A trivial Antlr-based language, and an API for providing sample files (see antlr-formatting's
test-jar artifact) used in tests
 * **antlr-editing-plugins** - Plugins for editing Antlr grammars in NetBeans
   * **adhoc-mime-types** - Registry of MIME types which are dynamically generated and can be mapped back to the origin
grammar - NetBeans' editing and parsing infrastructure is built around MIME types, and this allows that infrastructure
to host what is effectively a dynamically generated and updated language plugin for a grammar you are editing
   * **antlr-editing-kit** - No-code module which simply depends on all the pieces of Antlr grammar language support which
are stable, and is non-autoload
   * **antlr-error-highlighting** - Error highlighting and hints for Antlr grammars
   * **antlr-file-support** - Uses the annotations from `antlr-language-spi` to define `.g4` files as a NetBeans language -
Antlr grammar language support built using the tools above
   * **antlr-grammar-file-resolver** - Uses the plumbing in `antlr-project-helpers` to locate the Antlr folders for the
project and resolve imports
   * **antlr-in-memory-build** - Support for generating an Antlr grammar into JFS without touching disk, detect modifications
and rerun
   * **antlr-in-memory-execution** - Support for generating and running code in isolation, which calls a generated Antlr grammar
   * **antlr-language-code-completion** - Code completion for Antlr grammars
   * **antlr-language-formatting** - Formatter for Antlr grammars
   * **antlr-language-formatting-ui** - Formatting configuration UI for Antlr grammars
   * **antlr-language-grammar** - The Antlr grammar for Antlr grammars
   * **antlr-live** - Builds on `antlr-in-memory-build` to allow subscribing to reparses of a grammar, causing it to be
rebuilt into a per-project JFS instance
   * **antlr-live-execution** - Builds on `antlr-live` to allow running some code in an isolated classloader against a
grammar whenever it has been reparsed and rebuilt
   * **antlr-live-language-editors** - Full language support - replicates a bunch of the Editor's declarative plumbing
dynamically so normal editor features are available for documents in the (adhoc) mime type of an Antlr grammar, supports
associating a file extension with that grammar
   * **antlr-live-parsing** - Builds on `antlr-in-memory-execution` to generate, compile and run code to parse text using a dynamically
generated (JFS-based) grammar and extract a complete parse tree and language info without leaking classes from the
isolated environment
   * **antlr-live-preview** - Implements the preview tab for Antlr files
   * **antlr-project-extensions** - Decorates projects that support Antlr with source nodes for Antlr sources, implements
FileBuiltQuery for grammars and more
   * **antlr-project-helpers** - API for looking up Antlr configuration for a project, with an SPI to support this for
different project types
   * **antlr-project-helpers-maven** - Implementatino of the `antlr-project-helpers` SPI for Maven projects
   * **debug-api** - Used for debugging plugins - defines a lightweight API for tracking objects that should be garbage
collected eventually, and for defining high-level nested event contexts.  Near zero overhead unless `debug-ui` is
installed
   * **debug-ui** - Provides a UI for `debug-api` for debugging reparses and memory leaks
   * **java-file-grammar** - A Java grammar, used in the original Antlr plugin, and might be used here at some point
   * **parse-recorder** - An unfinished UI for animated visualization of the Antlr parsing process
   * **test-fixtures-support** - Support for generating and cleaning up temporary Antlr projects for tests, and for
more flexible support for a test programmatically registering things than NetBeans' `MockServices` allows, particularly
allowing for dynamic registration of named services
   * **tokens-file-grammar** - A grammar for tokens files from the original plugin - currently unused


### Dependencies

Only a handful of third-party libraries are used, most by the same author as this plugin.  Beyond that, there is
Antlr itself.  Specifically

 * Antlr (currently 4.7.2)
 * A few sub-libraries of [Mastfrog Utils](https://github.com/timboudreau/util):
    * **util-preconditions** - disableable runtime assertions for null checks and more
    * **util-collections** - optimized collections and collection-related utilities
    * **util-strings** - string related utilities
    * **range** - library for working with sets of bounded ranges, coalescing, etc. used for both highlighting and JFS's internal memory manager
    * **bits** - a wrapper for `BitSet`s and bit-set-like structures
    * **predicates** - adds primitive predicates missing from the JDK, and factories for *loggable* implementations of them
    * **graph** - high-performance, lightweight `BitSet`-based integer and object graphs
    * **abstractions** - dependency of some of the above
    * **function** - provides throwing equivalents for the `java.util.function` package contents, and missing function types
    * **java-vogon** - *only used by annotation processors* - Java code generation library
    * **annotation-tools** - *only used by annotation processors*

#### Miscellaneous Folders

  * **antlr-plugin** - The original Antlr plugin, where a lot of the antlr preview features were developed; no longer
buildable or in use, but some tests need to be moved out to their new homes, so it's here for now
  * **ANTLRTestProjects** - Test projects for the original plugin, non-automated, will be useful if Ant-based project
maven support is reimplemented

At some point all of this may get moved to a clean Git repo without history, since the original project contained
a bunch of binary JARs which, since they're in history, are part of every checkout forever like it or not.

## Development / Build & Run

Projects are built against a source builds of NetBeans using [this project](https://github.com/timboudreau/netbeans-local-maven-repo-populator)
to populate the local repository.  Set the `netbeans.version` property in the master pom to change it globally.

The projects are currently *buildable* on JDK 8; it is not recommended, but can be done with 
the following caveats (the only reason this is permitted at all is currently my continuous
build server is using a hacked, hand-built build of OpenJDK 10 because Oracle stopped publishing JDKs
for Solaris):

 * You must use `-Dmaven.test.skip=true` - otherwise incompatibilities 
 * Building against random NetBeans versions may fail in tests with a `NoSuchMethodException` inside the
Surefire (test runner) Maven plugin, because the boot class path gets polluted with the version of Maven
that the NetBeans Maven module embeds, which has different class signatures.  Your best bet for development is to build
your own platform using the link above.  You will need to build the formatters API once with tests enabled so
that the test-jar artifact exists in your local Maven repository.
 * Some tests the annotation processors rely on do not give correct results on JDK 8, particularly those testing
that a generified type's erasure matches a raw class name.  This does not appear to affect the code generated
for this module, but is likely to be a problem elsewhere.

### Test-Running

The `nbm-maven-plugin` has the serious limitation that, unlike Ant-based module suites, there is
*no straightforward way* to run the platform with a collection of interdependent modules installed*,
except to create an `nbm-application` project and rebuild it every time you want to manually test
something.

Then `antlr-suite` project does that. Simply navigate to that directory and run `./runone` to launch it
(the user dir will be `~/.antlr/dev` and the binary will be `target/bin/antlr` - ugly but works).
