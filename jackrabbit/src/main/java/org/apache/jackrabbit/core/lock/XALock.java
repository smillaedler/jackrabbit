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
package org.apache.jackrabbit.core.lock;

import org.apache.jackrabbit.core.NodeImpl;
import org.apache.log4j.Logger;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

/**
 * Extension to standard lock implementation that works in XA environment.
 */
class XALock extends LockImpl {

    /**
     * Logger instance for this class.
     */
    private static final Logger log = Logger.getLogger(XALock.class);

    /**
     * XA lock manager.
     */
    private final XALockManager lockMgr;

    /**
     * Create a new instance of this class.
     * @param info lock information
     * @param node node holding lock
     */
    public XALock(XALockManager lockMgr, AbstractLockInfo info, Node node) {
        super(info, node);

        this.lockMgr = lockMgr;
    }

    /**
     * {@inheritDoc}
     * <p/>
     * Refresh lock information if XA environment has changed.
     */
    public boolean isLive() throws RepositoryException {
        if (info.mayChange()) {
            if (lockMgr.differentXAEnv(info)) {
                return lockMgr.holdsLock((NodeImpl) node);
            }
        }
        return super.isLive();
    }
}
