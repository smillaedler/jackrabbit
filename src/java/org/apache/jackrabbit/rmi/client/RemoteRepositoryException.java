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
package org.apache.jackrabbit.rmi.client;

import java.rmi.RemoteException;

import javax.jcr.RepositoryException;

/**
 * JCR-RMI remote exception. Used by the JCR-RMI client to wrap RMI errors
 * into RepositoryExceptions to avoid breaking the JCR interfaces.
 * <p>
 * Note that if a RemoteException is received by call with no declared
 * exceptions, then the RemoteException is wrapped into a
 * RemoteRuntimeException.
 *
 * @author Jukka Zitting
 */
public class RemoteRepositoryException extends RepositoryException {

    /**
     * Creates a RemoteRepositoryException based on the given RemoteException.
     *
     * @param ex the remote exception
     */
    public RemoteRepositoryException(RemoteException ex) {
        super(ex);
    }

}
