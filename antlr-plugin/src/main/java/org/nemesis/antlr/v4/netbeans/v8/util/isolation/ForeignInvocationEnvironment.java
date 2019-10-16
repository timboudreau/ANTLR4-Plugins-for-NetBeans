/*
 * Copyright 2016-2019 Tim Boudreau, Frédéric Yvon Vinet
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nemesis.antlr.v4.netbeans.v8.util.isolation;

import org.nemesis.jfs.isolation.IsolationClassLoader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nemesis.antlr.v4.netbeans.v8.util.isolation.ExitBlockingSecurityManager.BlockExitException;

/**
 *
 * @author Tim Boudreau
 */
public class ForeignInvocationEnvironment {

    private static final Logger LOG = Logger.getLogger(ForeignInvocationEnvironment.class.getName());
//    private final URL[] urls;
    private static final ThreadGroup ISOLATION_THREAD_GROUP = new ThreadGroup("antlr-isolation");
    private final Supplier<IsolationClassLoader<?>> classloaderFactory;

    public ForeignInvocationEnvironment(Supplier<URL[]> supp) {
        this(null, defaultLoaderSupplier(supp.get()));
    }

    public ForeignInvocationEnvironment(Path... jars) {
        assert jars != null : "no jars";
        assert jars.length > 0 : "empty path array";
        URL[] theUrls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) {
            if (!Files.exists(jars[i])) {
                throw new IllegalArgumentException("File does not exist: " + jars[i]);
            }
            try {
                theUrls[i] = jars[i].toUri().toURL();
            } catch (MalformedURLException ex) {
                throw new AssertionError(ex);
            }
        }
        this.classloaderFactory = defaultLoaderSupplier(theUrls);
    }

    public ForeignInvocationEnvironment(URL... jarUrls) {
        assert jarUrls != null : "no urls";
        assert jarUrls.length > 0 : "empty url array";
        this.classloaderFactory = defaultLoaderSupplier(jarUrls);
    }

    private ForeignInvocationEnvironment(Void ignored, Supplier<IsolationClassLoader<?>> supp) {
        this.classloaderFactory = supp;
    }

    static Supplier<IsolationClassLoader<?>> defaultLoaderSupplier(URL... urls) {
        return new Supplier<IsolationClassLoader<?>>() {
            @Override
            public IsolationClassLoader<?> get() {
                return IsolationClassLoader.forURLs(urls, new Predicate<String>() {
                    @Override
                    public boolean test(String s) {
                        return s.startsWith("org.nemesis.antlr.v4");
                    }
                });
            }

            public String toString() {
                StringBuilder sb = new StringBuilder()
                        .append('{');
                for (int i = 0; i < urls.length; i++) {
                    URL u = urls[i];
                    sb.append(u);
                    if (i != urls.length - 1) {
                        sb.append(", ");
                    }
                }
                return sb.append('}').toString();
            }
        };
    }

    private IsolationClassLoader<?> createLoader() {
        return classloaderFactory.get();
    }

    @Override
    public String toString() {
        return ForeignInvocationEnvironment.class.getSimpleName() + "{"
                + classloaderFactory.toString() + "}";
    }

    public <T> ForeignInvocationResult<T> invoke(ReflectiveInvoker<T> invoker) {
        LOG.log(Level.FINE, "Foreign execution of {0} with {1}", new Object[]{invoker, this});
        PrintStream origOut = System.out;
        PrintStream origErr = System.err;
        AtomicReference<ForeignInvocationResult<T>> resultRef = new AtomicReference<>();
        ClassLoader origClassloader = Thread.currentThread().getContextClassLoader();
        SecurityManager oldSecurityManager = System.getSecurityManager();
//        ThreadGroup tg = new ThreadGroup(invoker.toString());
        ThreadGroup tg = ISOLATION_THREAD_GROUP;
        ExitBlockingSecurityManager blockExit = new ExitBlockingSecurityManager(tg);
        System.setSecurityManager(blockExit);
        IsolationClassLoader<?> loader = createLoader();
        // Run in our own thread and threadgroup, so the SecurityManager can say
        // what it applies to, and so we avoid even the possibility to screwing
        // with the calling thread's state
        Thread isolatedExecutionThread = new Thread(tg, () -> {
            ForeignInvocationResult<T> result = new ForeignInvocationResult<>(origOut, origErr);
            resultRef.set(result);
            try {
                Thread.currentThread().setContextClassLoader(loader);
                if (invoker.captureStandardOutputAndError()) {
                    System.setOut(result.stdout());
                    System.setErr(result.stderr());
                }
                T invocationResult = invoker.invoke(loader, result);
                LOG.log(Level.FINER, "Foreign execution of {0} with {1}"
                        + " returned {2}", new Object[]{invoker, this, invocationResult});
                result.invocationResult = invocationResult;
            } catch (BlockExitException ex) {
                LOG.log(Level.FINER, "Exception in foreign invocation of " + invoker, ex);
                result.exitCode = ex.code;
            } catch (InvocationTargetException ex) {
                if (ex.getCause() instanceof BlockExitException) {
                    result.exitCode = ((BlockExitException) ex.getCause()).code;
                    LOG.log(Level.FINER, "Exit attempt blocked in " + invoker, ex);
                } else {
                    LOG.log(Level.FINER, "Exception in foreign invocation of " + invoker, ex);
                    result.failure = ex.getCause();
                }
            } catch (Throwable ex) {
                result.failure = ex;
                LOG.log(Level.FINER, "Exception in foreign invocation of " + invoker, ex);
                Thread.currentThread().setContextClassLoader(origClassloader);
                if (invoker.captureStandardOutputAndError()) {
                    System.setOut(origOut);
                    System.setErr(origErr);
                }
                System.setSecurityManager(oldSecurityManager);
            } finally {
                try {
                    Thread.currentThread().setContextClassLoader(origClassloader);
                    if (invoker.captureStandardOutputAndError()) {

                        System.setOut(origOut);
                        System.setErr(origErr);
                    }
                    System.setSecurityManager(oldSecurityManager);
                } finally {
                    try {
                        loader.close();
                    } catch (IOException ioe) {
                        LOG.log(Level.INFO, "Exception closing classloader", ioe);
                    } finally {
                        result.closeOuts();
                    }
                }
            }
        }, invoker.toString());
        blockExit.setTargetThread(isolatedExecutionThread).start();
        try {
            isolatedExecutionThread.join();
        } catch (InterruptedException ex) {
            isolatedExecutionThread.interrupt();
            LOG.log(Level.INFO, "Interrupted waiting for isolated execution to return");
        }
        /*
        Need to delay calling ThreadGroup.destroy() or we get this, from
        the sampler module trying to start a timer in a dead ThreadGroup.
java.lang.IllegalThreadStateException
	at java.lang.ThreadGroup.addUnstarted(ThreadGroup.java:867)
	at java.lang.Thread.init(Thread.java:405)
	at java.lang.Thread.init(Thread.java:349)
	at java.lang.Thread.<init>(Thread.java:448)
	at java.util.TimerThread.<init>(Timer.java:499)
	at java.util.Timer.<init>(Timer.java:101)
	at org.netbeans.modules.sampler.Sampler.start(Sampler.java:158)
         */
//        RequestProcessor.getDefault().schedule(tg::destroy, 500, TimeUnit.MILLISECONDS);

        return resultRef.get();
    }
}
