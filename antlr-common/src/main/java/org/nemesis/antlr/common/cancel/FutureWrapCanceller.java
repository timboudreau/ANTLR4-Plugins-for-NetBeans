/*
 * Copyright 2016-2020 Tim Boudreau, Frédéric Yvon Vinet
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
package org.nemesis.antlr.common.cancel;

import java.util.concurrent.Future;

/**
 * Wrapper for a standard Canceller which will cancel a pending future as well.
 *
 * @author Tim Boudreau
 */
final class FutureWrapCanceller implements Canceller {

    private final CancellerImpl delegate;
    private final Future<?> future;

    FutureWrapCanceller(CancellerImpl delegate, Future<?> future) {
        this.delegate = delegate;
        this.future = future;
    }

    @Override
    public CancelledState cancel() {
        CancelledState result = delegate.cancel();
        if (result.isCancelledState()) {
            future.cancel(false);
        }
        return result;
    }

    @Override
    public boolean getAsBoolean() {
        return delegate.getAsBoolean();
    }

    @Override
    public CancelledState get() {
        return delegate.get();
    }
}
