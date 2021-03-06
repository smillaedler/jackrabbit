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
package org.apache.jackrabbit.core.virtual;

import org.apache.jackrabbit.core.state.ItemState;
import org.apache.jackrabbit.core.state.NoSuchItemStateException;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.name.QName;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.util.HashMap;
import java.util.HashSet;

/**
 * This Class implements a virtual node state
 */
public class VirtualNodeState extends NodeState {

    /**
     * The virtual item state provide that created this node state
     */
    protected final VirtualItemStateProvider stateMgr;

    /**
     * map of property states of this node state
     * key=propname, value={@link VirtualPropertyState}
     */
    private final HashMap properties = new HashMap();

    /** a set of hard references to child states */
    private HashSet stateRefs = null;

    /**
     * creates a new virtual node state
     *
     * @param stateMgr
     * @param parentUUID
     * @param uuid
     * @param nodeTypeName
     * @param mixins
     * @throws RepositoryException
     */
    public VirtualNodeState(AbstractVISProvider stateMgr,
                            String parentUUID,
                            String uuid,
                            QName nodeTypeName,
                            QName[] mixins)
            throws RepositoryException {
        super(uuid, nodeTypeName, parentUUID, ItemState.STATUS_EXISTING, false);
        this.stateMgr = stateMgr;
        addListener(stateMgr);
        // add default properties
        setPropertyValue(QName.JCR_PRIMARYTYPE, InternalValue.create(nodeTypeName));
        setMixinNodeTypes(mixins);
    }

    /**
     * Returns the properties of this node
     *
     * @return
     */
    public VirtualPropertyState[] getProperties() {
        return (VirtualPropertyState[]) properties.values().toArray(new VirtualPropertyState[properties.size()]);
    }


    /**
     * Returns the values of the given property of <code>null</code>
     *
     * @param name
     * @return
     */
    public InternalValue[] getPropertyValues(QName name) throws NoSuchItemStateException {
        VirtualPropertyState ps = getProperty(name);
        if (ps == null) {
            return null;
        } else {
            return ps.getValues();
        }
    }

    /**
     * Returns the value of the given property or <code>null</code>
     *
     * @param name
     * @return
     */
    public InternalValue getPropertyValue(QName name) throws NoSuchItemStateException {
        VirtualPropertyState ps = getProperty(name);
        if (ps == null || ps.getValues().length == 0) {
            return null;
        } else {
            return ps.getValues()[0];
        }
    }

    /**
     * returns the property state of the given name
     *
     * @param name
     * @return
     * @throws NoSuchItemStateException
     */
    public VirtualPropertyState getProperty(QName name) throws NoSuchItemStateException {
        return (VirtualPropertyState) properties.get(name);
    }

    /**
     * Sets the property value
     *
     * @param name
     * @param value
     * @throws javax.jcr.RepositoryException
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
        VirtualPropertyState prop = getOrCreatePropertyState(name, type, multiple);
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
    protected VirtualPropertyState getOrCreatePropertyState(QName name, int type, boolean multiValued)
            throws RepositoryException {

        VirtualPropertyState prop = (VirtualPropertyState) properties.get(name);
        if (prop == null) {
            prop = stateMgr.createPropertyState(this, name, type, multiValued);
            properties.put(name, prop);
            addPropertyName(name);
        }
        return prop;
    }

    /**
     * sets the mixing node type and adds the respective property
     *
     * @param mixins
     * @throws RepositoryException
     */
    public void setMixinNodeTypes(QName[] mixins) throws RepositoryException {
        if (mixins != null) {
            HashSet set = new HashSet();
            InternalValue[] values = new InternalValue[mixins.length];
            for (int i = 0; i < mixins.length; i++) {
                set.add(mixins[i]);
                values[i] = InternalValue.create(mixins[i]);
            }
            setMixinTypeNames(set);
            setPropertyValues(QName.JCR_MIXINTYPES, PropertyType.NAME, values);
        }
    }

    /**
     * Adds a hard reference to another state
     * @param state
     */
    public void addStateReference(NodeState state) {
        if (stateRefs == null) {
            stateRefs = new HashSet();
        }
        stateRefs.add(state);
    }
}
