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
package org.apache.jackrabbit.core.version;

import org.apache.jackrabbit.core.ItemLifeCycleListener;
import org.apache.jackrabbit.core.ItemManager;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.log4j.Logger;

import javax.jcr.Item;
import javax.jcr.RepositoryException;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.NodeIterator;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import java.util.Calendar;

/**
 * Base implementation of the {@link javax.jcr.version.Version} interface.
 */
public abstract class AbstractVersion extends NodeImpl implements Version {

    /**
     * Logger instance.
     */
    private static Logger log = Logger.getLogger(AbstractVersion.class);

    /**
     * Create a new instance of this class.
     * @param itemMgr item manager
     * @param session session
     * @param id node id
     * @param state node state
     * @param definition node definition
     * @param listeners life cycle listeners
     */
    protected AbstractVersion(ItemManager itemMgr, SessionImpl session, NodeId id,
                          NodeState state, NodeDefinition definition,
                          ItemLifeCycleListener[] listeners) {
        super(itemMgr, session, id, state, definition, listeners);
    }

    /**
     * Returns the internal version. Subclass responsibility.
     * @return internal version
     * @throws RepositoryException if the internal version is not available
     */
    protected abstract InternalVersion getInternalVersion()
            throws RepositoryException;

    /**
     * {@inheritDoc}
     */
    public Calendar getCreated() throws RepositoryException {
        return getInternalVersion().getCreated();
    }

    /**
     * {@inheritDoc}
     */
    public Version[] getSuccessors() throws RepositoryException {
        // need to wrap it around proper node
        InternalVersion[] suc = getInternalVersion().getSuccessors();
        Version[] ret = new Version[suc.length];
        for (int i = 0; i < suc.length; i++) {
            ret[i] = (Version) session.getNodeByUUID(suc[i].getId());
        }
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    public Version[] getPredecessors() throws RepositoryException {
        // need to wrap it around proper node
        InternalVersion[] pred = getInternalVersion().getPredecessors();
        Version[] ret = new Version[pred.length];
        for (int i = 0; i < pred.length; i++) {
            ret[i] = (Version) session.getNodeByUUID(pred[i].getId());
        }
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    public String getUUID() throws UnsupportedRepositoryOperationException, RepositoryException {
        return getInternalVersion().getId();
    }

    /**
     * {@inheritDoc}
     */
    public VersionHistory getContainingHistory() throws RepositoryException {
        return (VersionHistory) getParent();
    }

    /**
     * Returns the frozen node of this version
     *
     * @return
     * @throws javax.jcr.RepositoryException
     */
    public InternalFrozenNode getFrozenNode() throws RepositoryException {
        return getInternalVersion().getFrozenNode();
    }

    /**
     * {@inheritDoc}
     */
    public boolean isSame(Item otherItem) {
        if (otherItem instanceof AbstractVersion) {
            // since all versions live in the same workspace, we can compare the uuids
            try {
                InternalVersion other = ((AbstractVersion) otherItem).getInternalVersion();
                return other.getId().equals(getInternalVersion().getId());
            } catch (RepositoryException e) {
                log.warn("Unable to retrieve internal version objects: " + e.getMessage());
                log.debug("Stack dump:", e);
            }
        }
        return false;
    }

    /**
     * Checks if this version is more recent than the given version <code>v</code>.
     * A version is more recent if and only if it is a successor (or a successor
     * of a successor, etc., to any degree of separation) of the compared one.
     *
     * @param v the version to check
     * @return <code>true</code> if the version is more recent;
     *         <code>false</code> otherwise.
     */
    public boolean isMoreRecent(AbstractVersion v) throws RepositoryException {
        return getInternalVersion().isMoreRecent(v.getInternalVersion());
    }

    /**
     * Checks if this is the root version.
     * @return <code>true</code> if this version is the root version;
     *         <code>false</code> otherwise.
     */
    public boolean isRootVersion() throws RepositoryException {
        return getInternalVersion().isRootVersion();
    }

    //--------------------------------------< Overwrite "protected" methods >---


    /**
     * Always throws a {@link javax.jcr.nodetype.ConstraintViolationException} since this node
     * is protected.
     *
     * @throws javax.jcr.nodetype.ConstraintViolationException
     */
    public void update(String srcWorkspaceName) throws ConstraintViolationException {
        String msg = "update operation not allowed on a version node: " + safeGetJCRPath();
        log.debug(msg);
        throw new ConstraintViolationException(msg);
    }

    /**
     * Always throws a {@link javax.jcr.nodetype.ConstraintViolationException} since this node
     * is protected.
     *
     * @throws javax.jcr.nodetype.ConstraintViolationException
     */
    public NodeIterator merge(String srcWorkspace, boolean bestEffort)
            throws ConstraintViolationException {
        String msg = "merge operation not allowed on a version node: " + safeGetJCRPath();
        log.debug(msg);
        throw new ConstraintViolationException(msg);
    }

}
