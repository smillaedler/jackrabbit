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
package org.apache.jackrabbit.core.observation;

import org.apache.jackrabbit.core.HierarchyManager;
import org.apache.jackrabbit.core.ItemId;
import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeImpl;
import org.apache.jackrabbit.core.state.ChangeLog;
import org.apache.jackrabbit.core.state.ItemState;
import org.apache.jackrabbit.core.state.ItemStateException;
import org.apache.jackrabbit.core.state.ItemStateManager;
import org.apache.jackrabbit.core.state.NoSuchItemStateException;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.name.MalformedPathException;
import org.apache.jackrabbit.name.Path;
import org.apache.jackrabbit.name.QName;
import org.apache.log4j.Logger;

import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Collections;

/**
 * The <code>EventStateCollection</code> class implements how {@link EventState}
 * objects are created based on the {@link org.apache.jackrabbit.core.state.ItemState}s
 * passed to the {@link #createEventStates} method.
 * <p/>
 * The basic sequence of method calls is:
 * <ul>
 * <li>{@link #createEventStates} or {@link #addAll} to create or add event
 * states to the collection</li>
 * <li>{@link #prepare} or {@link #prepareDeleted} to prepare the events. If
 * this step is omitted, EventListeners might see events of deleted item
 * they are not allowed to see.</li>
 * <li>{@link #dispatch()} to dispatch the events to the EventListeners.</li>
 * </ul>
 */
public final class EventStateCollection {

    /**
     * Logger instance for this class
     */
    private static Logger log = Logger.getLogger(EventStateCollection.class);

    /**
     * List of events
     */
    private final List events = new ArrayList();

    /**
     * Event dispatcher.
     */
    private final EventDispatcher dispatcher;

    /**
     * The session that created these events
     */
    private final SessionImpl session;

    /**
     * Creates a new empty <code>EventStateCollection</code>.
     *
     * @param dispatcher event dispatcher
     * @param session the session that created these events.
     */
    EventStateCollection(EventDispatcher dispatcher,
                         SessionImpl session) {
        this.dispatcher = dispatcher;
        this.session = session;
    }

    /**
     * Creates {@link EventState} instances from <code>ItemState</code>
     * <code>changes</code>.
     *
     * @param rootNodeUUID the UUID of the root node.
     * @param changes      the changes on <code>ItemState</code>s.
     * @param provider     an <code>ItemStateProvider</code> to provide <code>ItemState</code>
     *                     of items that are not contained in the <code>changes</code> collection.
     * @throws ItemStateException if an error occurs while creating events
     *                            states for the item state changes.
     */
    public void createEventStates(String rootNodeUUID, ChangeLog changes, ItemStateManager provider) throws ItemStateException {
        // create a hierarchy manager, that is based on the ChangeLog and
        // the ItemStateProvider
        // todo use CachingHierarchyManager ?
        ChangeLogBasedHierarchyMgr hmgr =
                new ChangeLogBasedHierarchyMgr(rootNodeUUID, provider, changes,
                        session.getNamespaceResolver());

        /**
         * Important:
         * Do NOT change the sequence of events generated unless there's
         * a very good reason for it! Some internal SynchronousEventListener
         * implementations depend on the order of the events fired.
         * LockManagerImpl for example expects that for any given path a
         * childNodeRemoved event is fired before a childNodeAdded event.  
         */

        // 1. modified items

        for (Iterator it = changes.modifiedStates(); it.hasNext();) {
            ItemState state = (ItemState) it.next();
            if (state.isNode()) {
                // node changed
                // covers the following cases:
                // 1) property added
                // 2) property removed
                // 3) child node added
                // 4) child node removed
                // 5) node moved
                // 6) node reordered
                // cases 1) and 2) are detected with added and deleted states
                // on the PropertyState itself.
                // cases 3) and 4) are detected with added and deleted states
                // on the NodeState itself.
                // in case 5) two or three nodes change. two nodes are changed
                // when a child node is renamed. three nodes are changed when
                // a node is really moved. In any case we are only interested in
                // the node that actually got moved.
                // in case 6) only one node state changes. the state of the
                // parent node.
                NodeState n = (NodeState) state;

                if (n.hasOverlayedState()) {
                    String oldParentUUID = n.getOverlayedState().getParentUUID();
                    String newParentUUID = n.getParentUUID();
                    if (newParentUUID != null &&
                            !oldParentUUID.equals(newParentUUID)) {
                        // node moved
                        // generate node removed & node added event
                        NodeState oldParent;
                        try {
                            oldParent = (NodeState) changes.get(new NodeId(oldParentUUID));
                        } catch (NoSuchItemStateException e) {
                            // old parent has been deleted, retrieve from
                            // shared item state manager
                            oldParent = (NodeState) provider.getItemState(new NodeId(oldParentUUID));
                        }

                        NodeTypeImpl oldParentNodeType = getNodeType(oldParent, session);
                        Set mixins = oldParent.getMixinTypeNames();
                        Path newPath = getPath(n.getId(), hmgr);
                        Path oldPath = getZombiePath(n.getId(), hmgr);
                        if (!oldPath.equals(newPath)) {
                            events.add(EventState.childNodeRemoved(oldParentUUID,
                                    getParent(oldPath),
                                    n.getUUID(),
                                    oldPath.getNameElement(),
                                    oldParentNodeType,
                                    mixins,
                                    session));

                            NodeState newParent = (NodeState) changes.get(new NodeId(newParentUUID));
                            NodeTypeImpl newParentNodeType = getNodeType(newParent, session);
                            mixins = newParent.getMixinTypeNames();
                            events.add(EventState.childNodeAdded(newParentUUID,
                                    getParent(newPath),
                                    n.getUUID(),
                                    newPath.getNameElement(),
                                    newParentNodeType,
                                    mixins,
                                    session));
                        } else {
                            log.error("Unable to calculate old path of moved node");
                        }
                    } else {
                        // a moved node always has a modified parent node
                        NodeState parent = null;
                        try {
                            // root node does not have a parent UUID
                            if (state.getParentUUID() != null) {
                                parent = (NodeState) changes.get(new NodeId(state.getParentUUID()));
                            }
                        } catch (NoSuchItemStateException e) {
                            // should never happen actually. this would mean
                            // the parent of this modified node is deleted
                            String msg = "Parent of node " + state.getId() + " is deleted.";
                            log.error(msg);
                            throw new ItemStateException(msg, e);
                        }
                        if (parent != null) {
                            // check if node has been renamed
                            NodeState.ChildNodeEntry moved = null;
                            for (Iterator removedNodes = parent.getRemovedChildNodeEntries().iterator(); removedNodes.hasNext();) {
                                NodeState.ChildNodeEntry child = (NodeState.ChildNodeEntry) removedNodes.next();
                                if (child.getUUID().equals(n.getUUID())) {
                                    // found node re-added with different name
                                    moved = child;
                                }
                            }
                            if (moved != null) {
                                NodeTypeImpl nodeType = getNodeType(parent, session);
                                Set mixins = parent.getMixinTypeNames();
                                Path newPath = getPath(state.getId(), hmgr);
                                Path parentPath = getParent(newPath);
                                Path oldPath;
                                try {
                                    if (moved.getIndex() == 0) {
                                        oldPath = Path.create(parentPath, moved.getName(), false);
                                    } else {
                                        oldPath = Path.create(parentPath, moved.getName(), moved.getIndex(), false);
                                    }
                                } catch (MalformedPathException e) {
                                    // should never happen actually
                                    String msg = "Malformed path for item: " + state.getId();
                                    log.error(msg);
                                    throw new ItemStateException(msg, e);
                                }
                                events.add(EventState.childNodeRemoved(parent.getUUID(),
                                        parentPath,
                                        n.getUUID(),
                                        oldPath.getNameElement(),
                                        nodeType,
                                        mixins,
                                        session));
                                events.add(EventState.childNodeAdded(parent.getUUID(),
                                        parentPath,
                                        n.getUUID(),
                                        newPath.getNameElement(),
                                        nodeType,
                                        mixins,
                                        session));
                            }
                        }
                    }
                }

                // check if child nodes of modified node state have been reordered
                List reordered = n.getReorderedChildNodeEntries();
                NodeTypeImpl nodeType = getNodeType(n, session);
                Set mixins = n.getMixinTypeNames();
                if (reordered.size() > 0) {
                    // create a node removed and a node added event for every
                    // reorder
                    for (Iterator ro = reordered.iterator(); ro.hasNext();) {
                        NodeState.ChildNodeEntry child = (NodeState.ChildNodeEntry) ro.next();
                        QName name = child.getName();
                        int index = (child.getIndex() != 1) ? child.getIndex() : 0;
                        Path parentPath = getPath(n.getId(), hmgr);
                        Path.PathElement addedElem = Path.create(name, index).getNameElement();
                        // get removed index
                        NodeState overlayed = (NodeState) n.getOverlayedState();
                        NodeState.ChildNodeEntry entry = overlayed.getChildNodeEntry(child.getUUID());
                        if (entry == null) {
                            throw new ItemStateException("Unable to retrieve old child index for item: " + child.getUUID());
                        }
                        int oldIndex = (entry.getIndex() != 1) ? entry.getIndex() : 0;
                        Path.PathElement removedElem = Path.create(name, oldIndex).getNameElement();

                        events.add(EventState.childNodeRemoved(n.getUUID(),
                                parentPath,
                                child.getUUID(),
                                removedElem,
                                nodeType,
                                mixins,
                                session));

                        events.add(EventState.childNodeAdded(n.getUUID(),
                                parentPath,
                                child.getUUID(),
                                addedElem,
                                nodeType,
                                mixins,
                                session));
                    }
                }
            } else {
                // property changed
                Path path = getPath(state.getId(), hmgr);
                NodeState parent = (NodeState) provider.getItemState(new NodeId(state.getParentUUID()));
                NodeTypeImpl nodeType = getNodeType(parent, session);
                Set mixins = parent.getMixinTypeNames();
                events.add(EventState.propertyChanged(state.getParentUUID(),
                        getParent(path),
                        path.getNameElement(),
                        nodeType,
                        mixins,
                        session));
            }
        }

        // 2. removed items

        for (Iterator it = changes.deletedStates(); it.hasNext();) {
            ItemState state = (ItemState) it.next();
            if (state.isNode()) {
                // node deleted
                NodeState n = (NodeState) state;
                NodeState parent = (NodeState) provider.getItemState(new NodeId(n.getParentUUID()));
                NodeTypeImpl nodeType = getNodeType(parent, session);
                Set mixins = parent.getMixinTypeNames();
                Path path = getZombiePath(state.getId(), hmgr);
                events.add(EventState.childNodeRemoved(n.getParentUUID(),
                        getParent(path),
                        n.getUUID(),
                        path.getNameElement(),
                        nodeType,
                        mixins,
                        session));
            } else {
                // property removed
                // only create an event if node still exists
                try {
                    NodeState n = (NodeState) changes.get(new NodeId(state.getParentUUID()));
                    // node state exists -> only property removed
                    NodeTypeImpl nodeType = getNodeType(n, session);
                    Set mixins = n.getMixinTypeNames();
                    Path path = getZombiePath(state.getId(), hmgr);
                    events.add(EventState.propertyRemoved(state.getParentUUID(),
                            getParent(path),
                            path.getNameElement(),
                            nodeType,
                            mixins,
                            session));
                } catch (NoSuchItemStateException e) {
                    // node removed as well -> do not create an event
                }
            }
        }

        // 3. added items

        for (Iterator it = changes.addedStates(); it.hasNext();) {
            ItemState state = (ItemState) it.next();
            if (state.isNode()) {
                // node created
                NodeState n = (NodeState) state;
                NodeId parentId = new NodeId(n.getParentUUID());
                NodeState parent;
                // unknown if parent node is also new
                if (provider.hasItemState(parentId)) {
                    parent = (NodeState) provider.getItemState(parentId);
                } else {
                    parent = (NodeState) changes.get(parentId);
                }
                NodeTypeImpl nodeType = getNodeType(parent, session);
                Set mixins = parent.getMixinTypeNames();
                Path path = getPath(n.getId(), hmgr);
                events.add(EventState.childNodeAdded(n.getParentUUID(),
                        getParent(path),
                        n.getUUID(),
                        path.getNameElement(),
                        nodeType,
                        mixins,
                        session));
            } else {
                // property created / set
                NodeState n = (NodeState) changes.get(new NodeId(state.getParentUUID()));
                NodeTypeImpl nodeType = getNodeType(n, session);
                Set mixins = n.getMixinTypeNames();
                Path path = getPath(state.getId(), hmgr);
                events.add(EventState.propertyAdded(state.getParentUUID(),
                        getParent(path),
                        path.getNameElement(),
                        nodeType,
                        mixins,
                        session));
            }
        }
    }

    /**
     * Adds all event states in the given collection to this collection
     *
     * @param c
     */
    public void addAll(Collection c) {
        events.addAll(c);
    }

    /**
     * Prepares already added events for dispatching.
     */
    public void prepare() {
        dispatcher.prepareEvents(this);
    }

    /**
     * Prepares deleted items from <code>changes</code>.
     *
     * @param changes the changes to prepare.
     */
    public void prepareDeleted(ChangeLog changes) {
        dispatcher.prepareDeleted(this, changes);
    }

    /**
     * Dispatches the events to the {@link javax.jcr.observation.EventListener}s.
     */
    public void dispatch() {
        dispatcher.dispatchEvents(this);
    }

    /**
     * Returns an iterator over {@link EventState} instance.
     *
     * @return an iterator over {@link EventState} instance.
     */
    Iterator iterator() {
        return events.iterator();
    }

    /**
     * Return the list of events.
     * @return list of events
     */
    List getEvents() {
        return Collections.unmodifiableList(events);
    }

    /**
     * Return the session who is the origin of this events.
     * @return event source
     */
    SessionImpl getSession() {
        return session;
    }

    /**
     * Resolves the node type name in <code>node</code> into a {@link NodeType}
     * object using the {@link NodeTypeManager} of <code>session</code>.
     *
     * @param node    the node.
     * @param session the session.
     * @return the {@link NodeType} of <code>node</code>.
     * @throws ItemStateException if the nodetype cannot be resolved.
     */
    private NodeTypeImpl getNodeType(NodeState node, SessionImpl session)
            throws ItemStateException {
        try {
            return session.getNodeTypeManager().getNodeType(node.getNodeTypeName());
        } catch (Exception e) {
            // also catch eventual runtime exceptions here
            // should never happen actually
            String msg = "Item " + node.getId() + " has unknown node type: " + node.getNodeTypeName();
            log.error(msg);
            throw new ItemStateException(msg, e);
        }
    }

    /**
     * Returns the path of the parent node of node at <code>path</code>..
     *
     * @param p the path.
     * @return the parent path.
     * @throws ItemStateException if <code>p</code> does not have a parent
     *                            path. E.g. <code>p</code> designates root.
     */
    private Path getParent(Path p) throws ItemStateException {
        try {
            return p.getAncestor(1);
        } catch (PathNotFoundException e) {
            // should never happen actually
            String msg = "Unable to resolve parent for path: " + p;
            log.error(msg);
            throw new ItemStateException(msg, e);
        }
    }

    /**
     * Resolves the path of the Item with id <code>itemId</code>.
     *
     * @param itemId the id of the item.
     * @return the path of the item.
     * @throws ItemStateException if the path cannot be resolved.
     */
    private Path getPath(ItemId itemId, HierarchyManager hmgr)
            throws ItemStateException {
        try {
            return hmgr.getPath(itemId);
        } catch (RepositoryException e) {
            // should never happen actually
            String msg = "Unable to resolve path for item: " + itemId;
            log.error(msg);
            throw new ItemStateException(msg, e);
        }
    }

    /**
     * Resolves the <i>zombie</i> (i.e. the old) path of the Item with id
     * <code>itemId</code>.
     *
     * @param itemId the id of the item.
     * @return the path of the item.
     * @throws ItemStateException if the path cannot be resolved.
     */
    private Path getZombiePath(ItemId itemId, ChangeLogBasedHierarchyMgr hmgr)
            throws ItemStateException {
        try {
            return hmgr.getZombiePath(itemId);
        } catch (RepositoryException e) {
            // should never happen actually
            String msg = "Unable to resolve zombie path for item: " + itemId;
            log.error(msg);
            throw new ItemStateException(msg, e);
        }
    }
}
