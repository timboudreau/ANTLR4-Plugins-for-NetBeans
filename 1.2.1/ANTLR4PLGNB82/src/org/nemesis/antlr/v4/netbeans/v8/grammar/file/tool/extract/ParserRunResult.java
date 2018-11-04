package org.nemesis.antlr.v4.netbeans.v8.grammar.file.tool.extract;

import java.util.Optional;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ForeignInvocationResult;

/**
 *
 * @author Tim Boudreau
 */
public final class ParserRunResult {

    private final Optional<Throwable> failure;
    private final boolean success;
    private final Optional<AntlrProxies.ParseTreeProxy> parseTree;

    ParserRunResult(ForeignInvocationResult<AntlrProxies.ParseTreeProxy> res) {
        this(res.getFailure(), Optional.ofNullable(res.invocationResult()), res.isSuccess());
    }

    public ParserRunResult(Optional<Throwable> failure, Optional<AntlrProxies.ParseTreeProxy> parseTree, boolean success) {
        this.failure = failure;
        this.success = success;
        this.parseTree = parseTree;
    }

    public void rethrow() throws Throwable {
        if (failure.isPresent()) {
            throw failure.get();
        }
    }

    public boolean isUsable() {
        return success && !failure.isPresent() && parseTree.isPresent();
    }

    public Optional<Throwable> thrown() {
        return failure;
    }

    public Optional<AntlrProxies.ParseTreeProxy> parseTree() {
        return parseTree;
    }
}
