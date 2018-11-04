/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import java.util.Optional;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.AntlrSourceGenerationResult;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ParseTreeProxy;
import org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract.AntlrProxies.ProxySyntaxError;

/**
 *
 * @author Tim Boudreau
 */
public class GenerateBuildAndRunGrammarResult {

    private final AntlrSourceGenerationResult generationResult;
    private final Optional<CompileResult> compileResult;
    private final Optional<ParserRunResult> parserRunResult;
    private final String text;
    private final boolean compiled;
    private final boolean parsed;

    public GenerateBuildAndRunGrammarResult(AntlrSourceGenerationResult generationResult,
            Optional<CompileResult> compileResult,
            Optional<ParserRunResult> parserResult, String text,
            boolean compiled, boolean parsed) {
        this.generationResult = generationResult;
        this.compileResult = compileResult;
        this.parserRunResult = parserResult;
        this.text = text;
        this.compiled = compiled;
        this.parsed = parsed;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("ParseResult len=" + (text == null ? -1 : text.length()));
        sb.append(" usable=").append(isUsable());
        sb.append(" compiled=").append(compiled).append(" parsed=").append(parsed);
        sb.append("\n").append("generationResult=").append(generationResult);
        sb.append("\n").append("compileResult=");
        if (compileResult.isPresent()) {
            CompileResult res = compileResult.get();
            sb.append(" success=").append(res.callResult);
            if (res.thrown().isPresent()) {
                sb.append(" thrown=").append(res.thrown().get());
            } else {
                sb.append(" thrown=null");
            }
            sb.append(" usable=").append(res.isUsable());
            if (res.diagnostics().size() > 0) {
                sb.append(" diags={");
                for (JavacDiagnostic d : res.diagnostics()) {
                    sb.append('<').append(d.toString().replace('\n', ' ')).append('>');
                }
                sb.append('}');
            }
        } else {
            sb.append("<absent>");
        }
        sb.append("\n").append("parseResult=");
        if (parserRunResult.isPresent()) {
            ParserRunResult res = parserRunResult.get();
            if (res.thrown().isPresent()) {
                sb.append(" thrown=").append(res.thrown().get());
            } else {
                sb.append(" thrown=null");
            }
            if (res.parseTree().isPresent()) {
                ParseTreeProxy px = res.parseTree().get();
                sb.append(" tree=").append(px.summary());
                if (px.thrown() != null) {
                    px.thrown().printStackTrace();
                }
                if (!px.syntaxErrors().isEmpty()) {
                    sb.append(" syntaxErrors=");
                    for (ProxySyntaxError e : px.syntaxErrors()) {
                        sb.append('<').append(e).append('>');
                    }
                }
                sb.append(" pxthrown=").append(px.thrown());
            } else {
                sb.append(" tree=<absent>");
            }
        } else {
            sb.append("<absent>");
        }
        return sb.toString();
    }

    public boolean wasCompiled() {
        return compiled;
    }

    public boolean wasParsed() {
        return parsed;
    }

    public void rethrow() throws Throwable {
        Optional<Throwable> thrown = thrown();
        if (thrown.isPresent()) {
            throw thrown.get();
        } else if (parserRunResult.isPresent()) {
            if (parserRunResult.get().parseTree().isPresent()) {
                Throwable t = parserRunResult.get().parseTree().get().thrown();
                if (t != null) {
                    throw t;
                }
            }
        }
    }

    public String text() {
        return text;
    }

    public boolean isUsable() {
        if (!generationResult.isUsable()) {
            return false;
        }
        if (compiled) {
            if (!compileResult.isPresent()) {
                return false;
            } else {
                if (!compileResult.get().isUsable()) {
                    return false;
                }
            }
        }
        if (parsed) {
            if (!this.parserRunResult.isPresent()) {
            } else {
                if (!parserRunResult.get().isUsable()) {
                    return false;
                } else {
                    if (!parserRunResult.get().parseTree().isPresent()) {
                        return false;
                    } else {
                        ParseTreeProxy prox = parserRunResult.get().parseTree().get();
                        if (prox.thrown() != null) {
                            prox.thrown().printStackTrace();
                            return false;
                        } else {
//                            if (!prox.syntaxErrors().isEmpty()) {
//                                System.out.println("Extraction has syntax errors: " + prox.syntaxErrors());
//                                return false;
//                            }
                            return true;
                        }
                    }
                }
            }
        }
        return true;
    }

    public Optional<Throwable> thrown() {
        Optional<Throwable> result = generationResult.thrown();
        if (!result.isPresent()) {
            if (compileResult.isPresent()) {
                result = compileResult.get().thrown();
                if (!result.isPresent() && parserRunResult.isPresent()) {
                    result = parserRunResult.get().thrown();
                    if (!result.isPresent()) {
                        if (parserRunResult.get().parseTree().isPresent()) {
                            Throwable t = parserRunResult.get().parseTree().get().thrown();
                            result = Optional.ofNullable(t);
                        }
                    }
                }
            }
        }
        return result;
    }

    public AntlrSourceGenerationResult generationResult() {
        return generationResult;
    }

    public Optional<CompileResult> compileResult() {
        return compileResult;
    }

    public Optional<ParserRunResult> parseResult() {
        return parserRunResult;
    }

    public boolean onSuccess(ParseConsumer consumer) {
        if (isUsable()) {
            consumer.accept(generationResult, compileResult.isPresent() ? compileResult.get() : null,
                    parserRunResult.isPresent() ? parserRunResult.get() : null);
            return true;
        }
        return false;
    }

    public interface ParseConsumer {

        void accept(AntlrSourceGenerationResult genResult, CompileResult compileResult, ParserRunResult parserRunResult);
    }

}
