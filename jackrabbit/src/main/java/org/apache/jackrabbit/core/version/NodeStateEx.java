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

import org.apache.jackrabbit.core.NodeId;
import org.apache.jackrabbit.core.PropertyId;
import org.apache.jackrabbit.core.PropertyImpl;
import org.apache.jackrabbit.core.nodetype.EffectiveNodeType;
import org.apache.jackrabbit.core.nodetype.NodeDef;
import org.apache.jackrabbit.core.nodetype.NodeTypeConflictException;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.core.nodetype.PropDef;
import org.apache.jackrabbit.core.state.ItemState;
import org.apache.jackrabbit.core.state.ItemStateException;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.core.state.PropertyState;
import org.apache.jackrabbit.core.state.UpdatableItemStateManager;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.name.QName;
import org.apache.jackrabbit.uuid.UUID;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This Class provides some basic node operations directly on the node state.
 */
class NodeStateEx {

    /**
     * the underlying persistent state
     */
    private NodeState nodeState;

    /**
     * the state manager
     */
    private final UpdatableItemStateManager stateMgr;

    /**
     * the node type registry for resolving item defs
     */
    private final NodeTypeRegistry ntReg;

    /**
     * the cached name
     */
    private QName name;

    /**
     * Creates a new persistent node
     *
     * @param stateMgr
     * @param nodeState
     */
    public NodeStateEx(UpdatableItemStateManager stateMgr,
                       NodeTypeRegistry ntReg,
                       NodeState nodeState, QName name) {
        this.nodeState = nodeState;
        this.ntReg = ntReg;
        this.stateMgr = stateMgr;
        this.name = name;
    }


    /**
     * returns the name of this node
     *
     * @return
     */
    public QName getName() {
        if (name == null) {
            try {
                String parentId = nodeState.getParentUUID();
                NodeState parent = (NodeState) stateMgr.getItemState(new NodeId(parentId));
                name = parent.getChildNodeEntry(nodeState.getUUID()).getName();
            } catch (ItemStateException e) {
                // should never occurr
                throw new IllegalStateException(e.toString());
            }
        }
        return name;
    }

    /**
     * Returns the uuid of this node
     *
     * @return
     */
    public String getUUID() {
        return nodeState.getUUID();
    }

    /**
     * Returns the parent uuid of this node
     *
     * @return
     */
    public String getParentUUID() {
        return nodeState.getParentUUID();
    }

    /**
     * Returns the node state wrpaee
     * @return
     */
    public NodeState getState() {
        return nodeState;
    }

    /**
     * Returns the properties of this node
     *
     * @return
     */
    public PropertyState[] getProperties() throws ItemStateException {
        Set set = nodeState.getPropertyNames();
        PropertyState[] props = new PropertyState[set.size()];
        int i = 0;
        for (Iterator iter = set.iterator(); iter.hasNext();) {
            QName propName = (QName) iter.next();
            PropertyId propId = new PropertyId(nodeState.getUUID(), propName);
            props[i++] = (PropertyState) stateMgr.getItemState(propId);
        }
        return props;
    }

    /**
     * Checks if the given property exists
     *
     * @param name
     * @return
     */
    public boolean hasProperty(QName name) {
        PropertyId propId = new PropertyId(nodeState.getUUID(), name);
        return stateMgr.hasItemState(propId);
    }

    /**
     * Returns the values of the given property of <code>null</code>
     *
     * @param name
     * @return
     */
    public InternalValue[] getPropertyValues(QName name) {
        PropertyId propId = new PropertyId(nodeState.getUUID(), name);
        try {
            PropertyState ps = (PropertyState) stateMgr.getItemState(propId);
            return ps.getValues();
        } catch (ItemStateException e) {
            return null;
        }
    }

    /**
     * Returns the value of the given property or <code>null</code>
     *
     * @param name
     * @return
     */
    public InternalValue getPropertyValue(QName name) {
        PropertyId propId = new PropertyId(nodeState.getUUID(), name);
        try {
            PropertyState ps = (PropertyState) stateMgr.getItemState(propId);
            return ps.getValues()[0];
        } catch (ItemStateException e) {
            return null;
        }
    }

    /**
     * Sets the property value
     *
     * @param name
     * @param value
     * @throws RepositoryException
     */
    public void setPropertyValue(QName name, InternalValue value)
            throws RepositoryException {
        setPropertyValues(name, value.getType(), new InternalValue[]{value}, false);
    }

    /**
     * Sets the property values
     *
     * @param name
     * @param type
     * @param values
     * @throws RepositoryException
     */
    public void setPropertyValues(QName name, int type, InternalValue[] values)
            throws RepositoryException {
        setPropertyValues(name, type, values, true);
    }

    /**
     * Sets the property values
     *
     * @param name
     * @param type
     * @param values
     * @throws RepositoryException
     */
    public void setPropertyValues(QName name, int type, InternalValue[] values, boolean multiple)
            throws RepositoryException {

        PropertyState prop = getOrCreatePropertyState(name, type, multiple);
        prop.setValues(values);
    }


    /**
     * Retrieves or creates a new property state as child property of this node
     *
     * @param name
     * @param type
     * @param multiValued
     * @return
     * @throws RepositoryException
     */
    private PropertyState getOrCreatePropertyState(QName name, int type, boolean multiValued)
            throws RepositoryException {

        PropertyId propId = new PropertyId(nodeState.getUUID(), name);
        if (stateMgr.hasItemState(propId)) {
            try {
                PropertyState propState = (PropertyState) stateMgr.getItemState(propId);
                // someone calling this method will always alter the property state, so set status to modified
                if (propState.getStatus() == ItemState.STATUS_EXISTING) {
                    propState.setStatus(ItemState.STATUS_EXISTING_MODIFIED);
                }
                // although this is not quite correct, we mark node as modified aswell
                if (nodeState.getStatus() == ItemState.STATUS_EXISTING) {
                    nodeState.setStatus(ItemState.STATUS_EXISTING_MODIFIED);
                }
                return propState;
            } catch (ItemStateException e) {
                throw new RepositoryException("Unable to create property: " + e.toString());
            }
        } else {
            PropertyState propState = stateMgr.createNew(name, nodeState.getUUID());
            propState.setType(type);
            propState.setMultiValued(multiValued);

            PropDef pd = getEffectiveNodeType().getApplicablePropertyDef(name, type, multiValued);
            propState.setDefinitionId(pd.getId());

            // need to store nodestate
            nodeState.addPropertyName(name);
            if (nodeState.getStatus() == ItemState.STATUS_EXISTING) {
                nodeState.setStatus(ItemState.STATUS_EXISTING_MODIFIED);
            }
            return propState;
        }
    }

    /**
     * Returns the effective (i.e. merged and resolved) node type representation
     * of this node's primary and mixin node types.
     *
     * @return the effective node type
     * @throws RepositoryException
     */
    public EffectiveNodeType getEffectiveNodeType() throws RepositoryException {

        // build effective node type of mixins & primary type
        // existing mixin's
        HashSet set = new HashSet((nodeState).getMixinTypeNames());
        // primary type
        set.add(nodeState.getNodeTypeName());
        try {
            return ntReg.getEffectiveNodeType((QName[]) set.toArray(new QName[set.size()]));
        } catch (NodeTypeConflictException ntce) {
            String msg = "internal error: failed to build effective node type for node " + nodeState.getUUID();
            throw new RepositoryException(msg, ntce);
        }
    }



    /**
     * checks if the given child node exists.
     *
     * @param name
     * @return
     */
    public boolean hasNode(QName name) {
        return nodeState.hasChildNodeEntry(name);
    }

    /**
     * removes the (first) child node with the given name.
     *
     * @param name
     * @return
     * @throws RepositoryException
     */
    public boolean removeNode(QName name) throws RepositoryException {
        return removeNode(name, 1);
    }

    /**
     * removes the child node with the given name and 1-based index
     *
     * @param name
     * @param index
     * @return
     * @throws RepositoryException
     */
    public boolean removeNode(QName name, int index) throws RepositoryException {
        try {
            NodeState.ChildNodeEntry entry = nodeState.getChildNodeEntry(name, index);
            if (entry == null) {
                return false;
            } else {
                ItemState state = stateMgr.getItemState(new NodeId(entry.getUUID()));
                stateMgr.destroy(state);
                nodeState.removeChildNodeEntry(name, index);
                nodeState.setStatus(ItemState.STATUS_EXISTING_MODIFIED);
                return true;
            }
        } catch (ItemStateException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * removes the property with the given name
     *
     * @param name
     * @return
     * @throws RepositoryException
     */
    public boolean removeProperty(QName name) throws RepositoryException {
        try {
            if (!nodeState.hasPropertyName(name)) {
                return false;
            } else {
                PropertyId propId = new PropertyId(nodeState.getUUID(), name);
                ItemState state = stateMgr.getItemState(propId);
                stateMgr.destroy(state);
                nodeState.removePropertyName(name);
                nodeState.setStatus(ItemState.STATUS_EXISTING_MODIFIED);
                return true;
            }
        } catch (ItemStateException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * retrieves the child node with the given name and 1-base index or
     * <code>null</code> if the node does not exist.
     *
     * @param name
     * @param index
     * @return
     * @throws RepositoryException
     */
    public NodeStateEx getNode(QName name, int index) throws RepositoryException {
        NodeState.ChildNodeEntry entry = nodeState.getChildNodeEntry(name, index);
        if (entry == null) {
            return null;
        }
        try {
            NodeState state = (NodeState) stateMgr.getItemState(new NodeId(entry.getUUID()));
            return new NodeStateEx(stateMgr, ntReg, state, name);
        } catch (ItemStateException e) {
            throw new RepositoryException("Unable to getNode: " + e.toString());
        }
    }

    /**
     * Adds a new child node with the given name
     *
     * @param nodeName
     * @param nodeTypeName
     * @return
     * @throws NoSuchNodeTypeException
     * @throws ConstraintViolationException
     * @throws RepositoryException
     */
    public NodeStateEx addNode(QName nodeName, QName nodeTypeName,
                                  String uuid, boolean referenceable)
            throws NoSuchNodeTypeException, ConstraintViolationException, RepositoryException {

        NodeStateEx node = createChildNode(nodeName, nodeTypeName, uuid);
        if (referenceable) {
            node.setPropertyValue(QName.JCR_UUID, InternalValue.create(node.getUUID()));
        }
        return node;
    }

    /**
     * creates a new child node
     *
     * @param name
     * @param uuid
     * @return
     */
    private NodeStateEx createChildNode(QName name, QName nodeTypeName, String uuid)
            throws RepositoryException {
        String parentUUID = nodeState.getUUID();
        // create a new node state
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();    // version 4 uuid
        }
        NodeState state = stateMgr.createNew(uuid, nodeTypeName, parentUUID);

        NodeDef cnd = getEffectiveNodeType().getApplicableChildNodeDef(name, nodeTypeName);
        state.setDefinitionId(cnd.getId());

        // create Node instance wrapping new node state
        NodeStateEx node = new NodeStateEx(stateMgr, ntReg, state, name);
        node.setPropertyValue(QName.JCR_PRIMARYTYPE, InternalValue.create(nodeTypeName));

        // add new child node entryn
        nodeState.addChildNodeEntry(name, state.getUUID());
        if (nodeState.getStatus() == ItemState.STATUS_EXISTING) {
            nodeState.setStatus(ItemState.STATUS_EXISTING_MODIFIED);
        }
        return node;
    }

    /**
     * returns all child nodes
     *
     * @return
     * @throws RepositoryException
     */
    public NodeStateEx[] getChildNodes() throws RepositoryException {
        try {
            List entries = nodeState.getChildNodeEntries();
            NodeStateEx[] children = new NodeStateEx[entries.size()];
            for (int i = 0; i < entries.size(); i++) {
                NodeState.ChildNodeEntry entry = (NodeState.ChildNodeEntry) entries.get(i);
                NodeState state = (NodeState) stateMgr.getItemState(new NodeId(entry.getUUID()));
                children[i] = new NodeStateEx(stateMgr, ntReg, state, entry.getName());
            }
            return children;
        } catch (ItemStateException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * stores the persistent state recursively
     *
     * @throws RepositoryException
     */
    public void store() throws RepositoryException {
        try {
            store(nodeState);
        } catch (ItemStateException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * stores the given persistent state recursively
     *
     * @param state
     * @throws ItemStateException
     */
    private void store(NodeState state)
            throws ItemStateException {

        if (state.getStatus() != ItemState.STATUS_EXISTING) {
            // first store all transient properties
            Set props = state.getPropertyNames();
            for (Iterator iter = props.iterator(); iter.hasNext();) {
                QName propName = (QName) iter.next();
                PropertyState pstate = (PropertyState) stateMgr.getItemState(new PropertyId(state.getUUID(), propName));
                if (pstate.getStatus() != ItemState.STATUS_EXISTING) {
                    stateMgr.store(pstate);
                }
            }
            // now store all child node entries
            List nodes = state.getChildNodeEntries();
            for (int i = 0; i < nodes.size(); i++) {
                NodeState.ChildNodeEntry entry = (NodeState.ChildNodeEntry) nodes.get(i);
                NodeState nstate = (NodeState) stateMgr.getItemState(new NodeId(entry.getUUID()));
                store(nstate);
            }
            // and store itself
            stateMgr.store(state);
        }
    }

    /**
     * reloads the persistent state recursively
     *
     * @throws RepositoryException
     */
    public void reload() throws RepositoryException {
        try {
            reload(nodeState);
            // refetch nodestate if discarded
            nodeState = (NodeState) stateMgr.getItemState(nodeState.getId());
        } catch (ItemStateException e) {
            throw new RepositoryException(e);
        }
    }

    /**
     * reloads the given persistent state recursively
     *
     * @param state
     * @throws ItemStateException
     */
    private void reload(NodeState state) throws ItemStateException {
        if (state.getStatus() != ItemState.STATUS_EXISTING) {
            // first discard all all transient properties
            Set props = state.getPropertyNames();
            for (Iterator iter = props.iterator(); iter.hasNext();) {
                QName propName = (QName) iter.next();
                PropertyState pstate = (PropertyState) stateMgr.getItemState(new PropertyId(state.getUUID(), propName));
                if (pstate.getStatus() != ItemState.STATUS_EXISTING) {
                    pstate.discard();
                }
            }
            // now reload all child node entries
            List nodes = state.getChildNodeEntries();
            for (int i = 0; i < nodes.size(); i++) {
                NodeState.ChildNodeEntry entry = (NodeState.ChildNodeEntry) nodes.get(i);
                NodeState nstate = (NodeState) stateMgr.getItemState(new NodeId(entry.getUUID()));
                reload(nstate);
            }
            // and reload itself
            state.discard();
        }
    }

    /**
     * copies a property
     *
     * @param prop
     * @throws RepositoryException
     */
    public void copyFrom(PropertyImpl prop) throws RepositoryException {
        if (prop.getDefinition().isMultiple()) {
            InternalValue[] values = prop.internalGetValues();
            int type;
            if (values.length > 0) {
                type = values[0].getType();
            } else {
                type = prop.getDefinition().getRequiredType();
            }
            InternalValue[] copiedValues = new InternalValue[values.length];
            for (int i = 0; i < values.length; i++) {
                copiedValues[i] = values[i].createCopy();
            }
            setPropertyValues(prop.getQName(), type, copiedValues);
        } else {
            setPropertyValue(prop.getQName(), prop.internalGetValue().createCopy());
        }
    }

}
