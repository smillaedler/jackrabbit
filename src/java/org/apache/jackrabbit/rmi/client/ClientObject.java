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

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeDef;
import javax.jcr.nodetype.NodeType;
import javax.jcr.nodetype.NodeTypeIterator;
import javax.jcr.nodetype.PropertyDef;

import org.apache.jackrabbit.rmi.iterator.ArrayNodeIterator;
import org.apache.jackrabbit.rmi.iterator.ArrayNodeTypeIterator;
import org.apache.jackrabbit.rmi.iterator.ArrayPropertyIterator;
import org.apache.jackrabbit.rmi.remote.RemoteItem;
import org.apache.jackrabbit.rmi.remote.RemoteNode;
import org.apache.jackrabbit.rmi.remote.RemoteNodeDef;
import org.apache.jackrabbit.rmi.remote.RemoteNodeType;
import org.apache.jackrabbit.rmi.remote.RemoteProperty;
import org.apache.jackrabbit.rmi.remote.RemotePropertyDef;

/**
 * Base class for client adapter objects. The only purpose of
 * this class is to centralize the handling of the
 * local adapter factory used by the client adapters to
 * instantiate new adapters.
 *
 * @author Jukka Zitting
 */
public class ClientObject {

    /** Local adapter factory. */
    protected LocalAdapterFactory factory;

    /**
     * Creates a basic client adapter that uses the given factory
     * to create new adapters.
     *
     * @param factory local adapter factory
     */
    protected ClientObject(LocalAdapterFactory factory) {
        this.factory = factory;
    }

    /**
     * Utility method to create a local adapter for a remote item.
     * This method introspects the remote reference to determine
     * whether to instantiate a {@link Property Property},
     * a {@link Node Node}, or an {@link Item Item} adapter using
     * the local adapter factory.
     *
     * @param session current session
     * @param remote remote item
     * @return local property, node, or item adapter
     */
    protected Item getItem(Session session, RemoteItem remote) {
        if (remote instanceof RemoteProperty) {
            return factory.getProperty(session, (RemoteProperty) remote);
        } else if (remote instanceof RemoteNode) {
            return factory.getNode(session, (RemoteNode) remote);
        } else {
            return factory.getItem(session, remote);
        }
    }

    /**
     * Utility method for creating a property iterator for an array
     * of remote properties. The properties in the returned iterator
     * are created using the local adapter factory.
     * <p>
     * A <code>null</code> input is treated as an empty array.
     *
     * @param session current session
     * @param remotes remote properties
     * @return local property iterator
     */
    protected PropertyIterator getPropertyIterator(
            Session session, RemoteProperty[] remotes) {
        if (remotes == null) {
            return new ArrayPropertyIterator(new Property[0]); // for safety
        }

        Property[] properties = new Property[remotes.length];
        for (int i = 0; i < remotes.length; i++) {
            properties[i] = factory.getProperty(session, remotes[i]);
        }
        return new ArrayPropertyIterator(properties);
    }

    /**
     * Utility method for creating a node iterator for an array
     * of remote nodes. The nodes in the returned iterator
     * are created using the local adapter factory.
     * <p>
     * A <code>null</code> input is treated as an empty array.
     *
     * @param session current session
     * @param remotes remote nodes
     * @return local node iterator
     */
    protected NodeIterator getNodeIterator(
            Session session, RemoteNode[] remotes) {
        if (remotes == null) {
            return new ArrayNodeIterator(new Node[0]); // for safety
        }

        Node[] nodes = new Node[remotes.length];
        for (int i = 0; i < remotes.length; i++) {
            nodes[i] = factory.getNode(session, remotes[i]);
        }
        return new ArrayNodeIterator(nodes);
    }

    /**
     * Utility method for creating an array of local node type adapters
     * for an array of remote node types. The node type adapters are created
     * using the local adapter factory.
     * <p>
     * A <code>null</code> input is treated as an empty array.
     *
     * @param remotes remote node types
     * @return local node type array
     */
    protected NodeType[] getNodeTypeArray(RemoteNodeType[] remotes) {
        if (remotes == null) {
            return new NodeType[0]; // for safety
        }

        NodeType[] types = new NodeType[remotes.length];
        for (int i = 0; i < remotes.length; i++) {
            types[i] = factory.getNodeType(remotes[i]);
        }
        return types;
    }

    /**
     * Utility method for creating an iterator of local node type adapters
     * for an array of remote node types. The node type adapters are created
     * using the local adapter factory.
     * <p>
     * A <code>null</code> input is treated as an empty array.
     *
     * @param remotes remote node types
     * @return local node type iterator
     */
    protected NodeTypeIterator getNodeTypeIterator(RemoteNodeType[] remotes) {
        return new ArrayNodeTypeIterator(getNodeTypeArray(remotes));
    }

    /**
     * Utility method for creating an array of local node definition
     * adapters for an array of remote node definitions. The node
     * definition adapters are created using the local adapter factory.
     * <p>
     * A <code>null</code> input is treated as an empty array.
     *
     * @param remotes remote node definitions
     * @return local node definition array
     */
    protected NodeDef[] getNodeDefArray(RemoteNodeDef[] remotes) {
        if (remotes == null) {
            return new NodeDef[0]; // for safety
        }

        NodeDef[] defs = new NodeDef[remotes.length];
        for (int i = 0; i < remotes.length; i++) {
            defs[i] = factory.getNodeDef(remotes[i]);
        }
        return defs;
    }

    /**
     * Utility method for creating an array of local property definition
     * adapters for an array of remote property definitions. The property
     * definition adapters are created using the local adapter factory.
     * <p>
     * A <code>null</code> input is treated as an empty array.
     *
     * @param remotes remote property definitions
     * @return local property definition array
     */
    protected PropertyDef[] getPropertyDefArray(RemotePropertyDef[] remotes) {
        if (remotes == null) {
            return new PropertyDef[0]; // for safety
        }

        PropertyDef[] defs = new PropertyDef[remotes.length];
        for (int i = 0; i < remotes.length; i++) {
            defs[i] = factory.getPropertyDef(remotes[i]);
        }
        return defs;
    }

}
