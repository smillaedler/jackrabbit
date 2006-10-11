/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.jcr2spi.state;

import org.apache.jackrabbit.util.WeakIdentityCollection;
import org.apache.jackrabbit.spi.ItemId;
import org.apache.jackrabbit.spi.IdFactory;
import org.apache.jackrabbit.spi.Event;
import org.apache.jackrabbit.name.Path;
import org.apache.jackrabbit.name.MalformedPathException;
import org.apache.jackrabbit.name.QName;
import org.apache.jackrabbit.jcr2spi.state.entry.ChildNodeEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.Set;

/**
 * <code>ItemState</code> represents the state of an <code>Item</code>.
 */
public abstract class ItemState implements ItemStateLifeCycleListener {

    /**
     * Logger instance
     */
    private static Logger log = LoggerFactory.getLogger(ItemState.class);

    /**
     * Flag used to distinguish workspace states from session states. The first
     * accepts call to {@link #refresh(Event, ChangeLog)}, while the latter
     * will be able to handle the various methods related to transient
     * modifications.
     */
    private final boolean isWorkspaceState;

    /**
     * the internal status of this item state
     */
    private int status;

    /**
     * Listeners (weak references)
     */
    private final transient Collection listeners = new WeakIdentityCollection(5);

    // TODO: check again...
    /**
     *  IdFactory used to build id of the states
     */
    final IdFactory idFactory;

    /**
     * The parent <code>NodeState</code> or <code>null</code> if this
     * instance represents the root node.
     */
    NodeState parent;

    /**
     * the backing persistent item state (may be null)
     */
    transient ItemState overlayedState;

    /**
     * Constructs a new unconnected item state
     *
     * @param parent
     * @param initialStatus the initial status of the item state object
     */
    protected ItemState(NodeState parent, int initialStatus, IdFactory idFactory,
                        boolean isWorkspaceState) {
        switch (initialStatus) {
            case Status.EXISTING:
            case Status.NEW:
                status = initialStatus;
                break;
            default:
                String msg = "illegal status: " + initialStatus;
                log.debug(msg);
                throw new IllegalArgumentException(msg);
        }
        this.parent = parent;
        overlayedState = null;

        this.idFactory = idFactory;
        this.isWorkspaceState = isWorkspaceState;
    }

    /**
     * Constructs a new item state that is initially connected to an overlayed
     * state.
     *
     * @param overlayedState the backing item state being overlayed
     * @param initialStatus the initial status of the new <code>ItemState</code> instance
     */
    protected ItemState(ItemState overlayedState, NodeState parent,
                        int initialStatus, IdFactory idFactory) {
        switch (initialStatus) {
            case Status.EXISTING:
            case Status.EXISTING_MODIFIED:
            case Status.EXISTING_REMOVED:
                status = initialStatus;
                break;
            default:
                String msg = "illegal status: " + initialStatus;
                log.debug(msg);
                throw new IllegalArgumentException(msg);
        }
        this.parent = parent;
        this.idFactory = idFactory;
        this.isWorkspaceState = false;

        connect(overlayedState);
    }

    //----------------------------------------------------------< ItemState >---
    /**
     * Returns <code>true</code> if this item state is valid, that is its status
     * is one of:
     * <ul>
     * <li>{@link Status#EXISTING}</li>
     * <li>{@link Status#EXISTING_MODIFIED}</li>
     * <li>{@link Status#NEW}</li>
     * </ul>
     * @return
     */
    public boolean isValid() {
        return status == Status.EXISTING || status == Status.EXISTING_MODIFIED || status == Status.NEW;
    }

    /**
     * Determines if this item state represents a node.
     *
     * @return true if this item state represents a node, otherwise false.
     */
    public abstract boolean isNode();

    /**
     * Returns the name of this state.
     *
     * @return name of this state
     */
    public abstract QName getQName();

    /**
     * Returns the identifier of this item state.
     *
     * @return the identifier of this item state..
     */
    public abstract ItemId getId();

    /**
     * Returns the qualified path of this item state.
     *
     * @return qualified path
     * @throws ItemNotFoundException
     * @throws RepositoryException
     */
    public Path getQPath() throws ItemNotFoundException, RepositoryException {
        // shortcut for root state
        if (getParent() == null) {
            return Path.ROOT;
        }

        // build path otherwise
        try {
            Path.PathBuilder builder = new Path.PathBuilder();
            buildPath(builder, this);
            return builder.getPath();
        } catch (NoSuchItemStateException e) {
            String msg = "Failed to build path of " + this;
            log.debug(msg);
            throw new ItemNotFoundException(msg, e);
        } catch (ItemStateException e) {
            String msg = "Failed to build path of " + this;
            log.debug(msg);
            throw new RepositoryException(msg, e);
        } catch (MalformedPathException e) {
            String msg = "Failed to build path of " + this;
            throw new RepositoryException(msg, e);
        }
    }

    /**
     * Adds the path element of an item id to the path currently being built.
     * On exit, <code>builder</code> contains the path of <code>state</code>.
     *
     * @param builder builder currently being used
     * @param state   item to find path of
     */
    private void buildPath(Path.PathBuilder builder, ItemState state)
            throws ItemStateException, RepositoryException {
        NodeState parentState = state.getParent();
        // shortcut for root state
        if (parentState == null) {
            builder.addRoot();
            return;
        }

        // recursively build path of parent
        buildPath(builder, parentState);

        if (state.isNode()) {
            NodeState nodeState = (NodeState) state;
            ChildNodeEntry entry = parentState.getChildNodeEntry(nodeState);
            if (entry == null) {
                String msg = "Failed to build path of " + state + ": parent has no such child entry.";
                log.debug(msg);
                throw new ItemNotFoundException(msg);
            }
            // add to path
            if (entry.getIndex() == Path.INDEX_DEFAULT) {
                builder.addLast(entry.getName());
            } else {
                builder.addLast(entry.getName(), entry.getIndex());
            }
        } else {
            PropertyState propState = (PropertyState) state;
            QName name = propState.getQName();
            // add to path
            builder.addLast(name);
        }
    }

    /**
     * Returns the parent <code>NodeState</code> or <code>null</code>
     * if either this item state represents the root node or this item state is
     * 'free floating', i.e. not attached to the repository's hierarchy.
     *
     * @return the parent <code>NodeState</code>
     */
    public NodeState getParent() {
        return parent;
    }

    /**
     * Returns the status of this item.
     *
     * @return the status of this item.
     */
    public int getStatus() {
        return status;
    }

    /**
     * Sets the new status of this item.
     *
     * @param newStatus the new status
     */
    void setStatus(int newStatus) {
        int oldStatus = status;
        if (oldStatus == newStatus) {
            return;
        }

        if (Status.isTerminalStatus(oldStatus)) {
            throw new IllegalStateException("State is already in terminal status " + oldStatus);
        }
        if (Status.isValidStatusChange(oldStatus, newStatus, isWorkspaceState)) {
            status = newStatus;
        } else {
            throw new IllegalArgumentException("Invalid new status " + newStatus + " for state with status " + oldStatus);
        }
        // notifiy listeners about status change
        // copy listeners to array to avoid ConcurrentModificationException
        ItemStateLifeCycleListener[] la;
        synchronized (listeners) {
            la = (ItemStateLifeCycleListener[]) listeners.toArray(new ItemStateLifeCycleListener[listeners.size()]);
        }
        for (int i = 0; i < la.length; i++) {
            if (la[i] instanceof ItemStateLifeCycleListener) {
                ((ItemStateLifeCycleListener) la[i]).statusChanged(this, oldStatus);
            }
        }
        if (status == Status.MODIFIED) {
            // change back tmp MODIFIED status, that is used only to have a marker
            // to inform the overlaying state, that it needs to synchronize with
            // its overlayed state again
            // TODO: improve...
            status = Status.EXISTING;
        }
    }

    /**
     * Add an <code>ItemStateLifeCycleListener</code>
     *
     * @param listener the new listener to be informed on modifications
     */
    public void addListener(ItemStateLifeCycleListener listener) {
        synchronized (listeners) {
            assert (!listeners.contains(listener));
            listeners.add(listener);
        }
    }

    /**
     * Remove an <code>ItemStateLifeCycleListener</code>
     *
     * @param listener an existing listener
     */
    public void removeListener(ItemStateLifeCycleListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    //--------------------------------------------------------< State types >---
    /**
     * @return true if this state is a workspace state.
     */
    public boolean isWorkspaceState() {
        return isWorkspaceState;
    }

    /**
     * Returns <i>this</i>, if {@link #isWorkspaceState()} returns <code>true</code>.
     * Otherwise this method returns the workspace state backing <i>this</i>
     * 'session' state or <code>null</code> if this state is new.
     *
     * @return the workspace state or <code>null</code> if this state is new.
     */
    public ItemState getWorkspaceState() {
        if (isWorkspaceState) {
            return this;
        } else {
            return overlayedState;
        }
    }

    /**
     * @throws IllegalStateException if this state is a 'session' state.
     */
    public void checkIsWorkspaceState() {
        if (!isWorkspaceState) {
            throw new IllegalStateException("State " + this + " is not a 'workspace' state.");
        }
    }

    /**
     * @throws IllegalStateException if this state is a 'workspace' state.
     */
    public void checkIsSessionState() {
        if (isWorkspaceState) {
            throw new IllegalStateException("State " + this + " is not a 'session' state.");
        }
    }

    //--------------------------------------------------< Workspace - State >---
    /**
     * Used on 'workspace' states in order to update the state according to
     * the given event (and ev. changelog).
     *
     * @param event
     * @param changeLog
     * @throws IllegalStateException if this state is a 'session' state.
     */
    abstract void refresh(Event event, ChangeLog changeLog);


    //----------------------------------------------------< Session - State >---
    /**
     * Copy all state information from overlayed state to this state
     */
    abstract void reset();

    /**
     * Merge the state information from the overlayed state into this state
     */
    abstract void merge();

    /**
     * Connect this state to some underlying overlayed state.
     */
    void connect(ItemState overlayedState) {
        checkIsSessionState();
        overlayedState.checkIsWorkspaceState();

        if (this.overlayedState != null && this.overlayedState != overlayedState) {
            throw new IllegalStateException("Item state already connected to another underlying state: " + this);
        }
        this.overlayedState = overlayedState;
        this.overlayedState.addListener(this);
    }

    /**
     * Returns <code>true</code> if this item state represents new or modified
     * state or <code>false</code> if it represents existing, unmodified state.
     *
     * @return <code>true</code> if this item state is modified or new,
     *         otherwise <code>false</code>
     */
    private boolean isTransient() {
        checkIsSessionState();
        return status == Status.EXISTING_MODIFIED || status == Status.NEW;
    }

    /**
     * Removes this item state. This will change the status of this property
     * state to either {@link Status#EXISTING_REMOVED} or {@link
     * Status#REMOVED} depending on the current status.
     *
     * @throws ItemStateException if an error occurs while removing this item
     *                            state. e.g. this item state is not valid
     *                            anymore.
     */
    abstract void remove() throws ItemStateException;

    /**
     * Reverts this item state to its initial status and adds itself to the Set
     * of <code>affectedItemStates</code> if it reverted itself.
     *
     * @param affectedItemStates the set of affected item states that reverted
     *                           themselfes.
     */
    abstract void revert(Set affectedItemStates);

    /**
     * Checks if this <code>ItemState</code> is transiently modified or new and
     * adds itself to the <code>Set</code> of <code>transientStates</code> if
     * that is the case. It this <code>ItemState</code> has children it will
     * call the method {@link #collectTransientStates(java.util.Set)} on those
     * <code>ItemState</code>s.
     *
     * @param transientStates the <code>Set</code> of transient <code>ItemState</code>,
     *                        collected while the <code>ItemState</code>
     *                        hierarchy is traversed.
     */
    abstract void collectTransientStates(Set transientStates);

    /**
     * Marks this item state as modified.
     */
    void markModified() {
        checkIsSessionState();

        switch (status) {
            case Status.EXISTING:
                setStatus(Status.EXISTING_MODIFIED);
                break;
            case Status.EXISTING_MODIFIED:
                // already modified, do nothing
                break;
            case Status.NEW:
                // still new, do nothing
                break;
            case Status.STALE_DESTROYED:
            case Status.STALE_MODIFIED:
                // should actually not get here because item should check before
                // it modifies an item state.
                throw new IllegalStateException("Cannot mark stale state modified.");

            case Status.EXISTING_REMOVED:
            default:
                String msg = "Cannot mark item state with status " + status + " modified.";
                throw new IllegalStateException(msg);
        }
    }
    //-----------------------------------------< ItemStateLifeCycleListener >---
    /**
     *
     * @param state
     * @param previousStatus
     */
    public void statusChanged(ItemState state, int previousStatus) {
        // workspace-states never are listening to another state
        checkIsSessionState();
        state.checkIsWorkspaceState();

        switch (state.getStatus()) {
            case Status.EXISTING:
                // nothing to do
                break;
            case Status.MODIFIED:
                if (previousStatus == Status.EXISTING) {
                    // underlying state has been modified
                    if (isTransient()) {
                        setStatus(Status.STALE_MODIFIED);
                    } else {
                        synchronized (this) {
                            // this instance represents existing state, update it
                            merge();
                            setStatus(Status.EXISTING);
                        }
                    }
                }
                break;
            case Status.REMOVED:
                if (isTransient()) {
                    setStatus(Status.STALE_DESTROYED);
                } else {
                    setStatus(Status.REMOVED);
                }
                break;
            default:
                // Should never occur, since 'setStatus(int)' already validates
                log.error("Workspace state cannot have its state changed to " + state.getStatus());
                break;
        }
    }
}
