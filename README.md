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




