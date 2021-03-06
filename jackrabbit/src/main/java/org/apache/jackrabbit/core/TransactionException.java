/*
 * Copyright 2004-2005 The Apache Software Foundation or its licensors,
 *                     as applicable.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.core;

import org.apache.jackrabbit.BaseException;

/**
 * TransactionException is thrown when some operation inside the transaction
 * fails.
 */
public class TransactionException extends BaseException {

    /**
     * Creates an instance of this class. Takes a detail message as parameter.
     *
     * @param message message
     */
    public TransactionException(String message) {
        super(message);
    }

    /**
     * Creates an instance of this class. Takes a root throwable as parameter.
     *
     * @param rootCause root throwable
     */
    public TransactionException(Throwable rootCause) {
        super(rootCause);
    }

    /**
     * Creates an instance of this class. Takes a message and a root throwable
     * as parameter.
     *
     * @param message   message
     * @param rootCause root throwable
     */
    public TransactionException(String message, Throwable rootCause) {
        super(message, rootCause);
    }
}
