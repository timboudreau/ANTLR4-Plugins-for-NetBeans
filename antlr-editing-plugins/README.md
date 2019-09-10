Antlr Grammar Editing Plugins for NetBeans
==========================================

The directory below this one is general-purpose tooling for generating most of
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


