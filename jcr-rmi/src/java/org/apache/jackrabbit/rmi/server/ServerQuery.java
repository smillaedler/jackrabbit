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
package org.apache.jackrabbit.rmi.server;

import java.rmi.RemoteException;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;

import org.apache.jackrabbit.rmi.remote.RemoteQuery;
import org.apache.jackrabbit.rmi.remote.RemoteQueryResult;
import org.apache.jackrabbit.rmi.remote.RemoteNode;

/**
 * Remote adapter for the JCR {@link javax.jcr.query.Query Query} interface.
 * This class makes a local session available as an RMI service using the
 * {@link org.apache.jackrabbit.rmi.remote.RemoteQuery RemoteQuery}
 * interface.
 *
 * @author Philipp Koch
 * @see javax.jcr.query.Query
 * @see org.apache.jackrabbit.rmi.remote.RemoteQuery
 */
public class ServerQuery extends ServerObject implements RemoteQuery {

    /** The adapted local query manager. */
    private Query query;

    /**
     * Creates a remote adapter for the given local <code>Query</code>.
     *
     * @param query local <code>Query</code>
     * @param factory remote adapter factory
     * @throws RemoteException on RMI errors
     */
    public ServerQuery(Query query, RemoteAdapterFactory factory)
            throws RemoteException {
        super(factory);
        this.query = query;
    }

    /** {@inheritDoc} */
    public RemoteQueryResult execute()
            throws RepositoryException, RemoteException {
        return new ServerQueryResult(query.execute(), getFactory());
    }

    /** {@inheritDoc} */
    public String getStatement() throws RemoteException {
        return query.getStatement();
    }

    /** {@inheritDoc} */
    public String getLanguage() throws RemoteException {
        return query.getLanguage();
    }

    /** {@inheritDoc} */
    public String getStoredQueryPath()
            throws RepositoryException, RemoteException {
        return query.getStoredQueryPath();
    }

    /** {@inheritDoc} */
    public RemoteNode storeAsNode(String absPath)
            throws RepositoryException, RemoteException {
        return getRemoteNode(query.storeAsNode(absPath));
    }

}
