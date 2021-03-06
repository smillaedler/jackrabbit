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
package org.apache.jackrabbit.core.query.lucene;

import org.apache.lucene.index.FilterIndexReader;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.Term;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.BitSet;
import java.io.IOException;

/**
 * Implements an <code>IndexReader</code>, that will close when all connected
 * clients are disconnected AND the <code>SharedIndexReader</code>s
 * <code>close()</code> method itself has been called.
 */
class SharedIndexReader extends FilterIndexReader {

    /**
     * Set to <code>true</code> if this index reader should be closed, when
     * all connected clients are disconnected.
     */
    private boolean closeRequested = false;

    /**
     * Map of all registered clients to this shared index reader. The Map
     * is rather used as a Set, because each value is the same Object as its
     * associated key.
     */
    private final Map clients = new IdentityHashMap();

    /**
     * Creates a new <code>SharedIndexReader</code> which is based on
     * <code>in</code>.
     * @param in the underlying <code>IndexReader</code>.
     */
    public SharedIndexReader(CachingIndexReader in) {
        super(in);
    }

    /**
     * Returns the <code>DocId</code> of the parent of <code>n</code> or
     * {@link DocId#NULL} if <code>n</code> does not have a parent
     * (<code>n</code> is the root node).
     *
     * @param n the document number.
     * @param deleted the documents that should be regarded as deleted.
     * @return the <code>DocId</code> of <code>n</code>'s parent.
     * @throws IOException if an error occurs while reading from the index.
     */
    public DocId getParent(int n, BitSet deleted) throws IOException {
        return getBase().getParent(n, deleted);
    }

    /**
     * Registeres <code>client</code> with this reader. As long as clients are
     * registered, this shared reader will not release resources on {@link
     * #close()} and will not actually close but only marks itself to close when
     * the last client is unregistered.
     *
     * @param client the client to register.
     */
    public synchronized void addClient(Object client) {
        clients.put(client, client);
    }

    /**
     * Unregisters the <code>client</code> from this index reader.
     *
     * @param client a client of this reader.
     * @throws IOException if an error occurs while detaching the client from
     *                     this shared reader.
     */
    public synchronized void removeClient(Object client) throws IOException {
        clients.remove(client);
        if (clients.isEmpty() && closeRequested) {
            super.doClose();
        }
    }

    /**
     * Closes this index if no client is registered, otherwise this reader is
     * marked to close when the last client is disconnected.
     *
     * @throws IOException if an error occurs while closing.
     */
    protected synchronized void doClose() throws IOException {
        if (clients.isEmpty()) {
            super.doClose();
        } else {
            closeRequested = true;
        }
    }

    /**
     * Simply passes the call to the wrapped reader as is.<br/>
     * If <code>term</code> is for a {@link FieldNames#UUID} field and this
     * <code>SharedIndexReader</code> does not have such a document,
     * {@link CachingIndexReader#EMPTY} is returned.
     *
     * @param term the term to enumerate the docs for.
     * @return TermDocs for <code>term</code>.
     * @throws IOException if an error occurs while reading from the index.
     */
    public TermDocs termDocs(Term term) throws IOException {
        return in.termDocs(term);
    }

    /**
     * Returns the {@link CachingIndexReader} this reader is based on.
     *
     * @return the {@link CachingIndexReader} this reader is based on.
     */
    public CachingIndexReader getBase() {
        return (CachingIndexReader) in;
    }

}
