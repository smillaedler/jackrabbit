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

import org.apache.jackrabbit.core.NodeImpl;
import org.apache.jackrabbit.core.PropertyImpl;
import org.apache.jackrabbit.core.nodetype.NodeTypeImpl;
import org.apache.jackrabbit.core.state.ItemStateException;
import org.apache.jackrabbit.core.state.NodeState;
import org.apache.jackrabbit.core.state.PropertyState;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.name.QName;
import org.apache.jackrabbit.uuid.UUID;

import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.OnParentVersionAction;
import javax.jcr.version.VersionException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implements a <code>InternalFrozenNode</code>
 */
class InternalFrozenNodeImpl extends InternalFreezeImpl
        implements InternalFrozenNode {

    /**
     * checkin mode version.
     */
    private static final int MODE_VERSION = 0;

    /**
     * checkin mode copy. specifies, that the items are always copied.
     */
    private static final int MODE_COPY = 1;

    /**
     * mode flag specifies, that the mode should be recursed. otherwise i
     * will be redetermined by the opv.
     */
    private static final int MODE_COPY_RECURSIVE = 3;

    /**
     * the underlying persistance node
     */
    private NodeStateEx node;

    /**
     * the list of frozen properties
     */
    private PropertyState[] frozenProperties;

    /**
     * the frozen uuid of the original node
     */
    private String frozenUUID = null;

    /**
     * the frozen primary type of the orginal node
     */
    private QName frozenPrimaryType = null;

    /**
     * the frozen list of mixin types of the original node
     */
    private QName[] frozenMixinTypes = null;

    /**
     * Creates a new frozen node based on the given persistance node.
     *
     * @param node
     * @throws javax.jcr.RepositoryException
     */
    public InternalFrozenNodeImpl(AbstractVersionManager vMgr, NodeStateEx node,
                                  InternalVersionItem parent)
            throws RepositoryException {
        super(vMgr, parent);
        this.node = node;

        // init the frozen properties
        PropertyState[] props;
        try {
            props = node.getProperties();
        } catch (ItemStateException e) {
            throw new RepositoryException(e);
        }
        List propList = new ArrayList();

        for (int i = 0; i < props.length; i++) {
            PropertyState prop = props[i];
            if (prop.getName().equals(QName.JCR_FROZENUUID)) {
                // special property
                frozenUUID = node.getPropertyValue(QName.JCR_FROZENUUID).internalValue().toString();
            } else if (prop.getName().equals(QName.JCR_FROZENPRIMARYTYPE)) {
                // special property
                frozenPrimaryType = (QName) node.getPropertyValue(QName.JCR_FROZENPRIMARYTYPE).internalValue();
            } else if (prop.getName().equals(QName.JCR_FROZENMIXINTYPES)) {
                // special property
                InternalValue[] values = node.getPropertyValues(QName.JCR_FROZENMIXINTYPES);
                if (values == null) {
                    frozenMixinTypes = new QName[0];
                } else {
                    frozenMixinTypes = new QName[values.length];
                    for (int j = 0; j < values.length; j++) {
                        frozenMixinTypes[j] = (QName) values[j].internalValue();
                    }
                }
            } else if (prop.getName().equals(QName.JCR_PRIMARYTYPE)) {
                // ignore
            } else if (prop.getName().equals(QName.JCR_UUID)) {
                // ignore
            } else {
                propList.add(prop);
            }
        }
        frozenProperties = (PropertyState[]) propList.toArray(new PropertyState[propList.size()]);

        // do some checks
        if (frozenMixinTypes == null) {
            frozenMixinTypes = new QName[0];
        }
        if (frozenPrimaryType == null) {
            throw new RepositoryException("Illegal frozen node. Must have 'frozenPrimaryType'");
        }
    }

    /**
     * {@inheritDoc}
     */
    public QName getName() {
        return node.getName();
    }

    /**
     * {@inheritDoc}
     */
    public String getId() {
        return node.getUUID();
    }

    /**
     * {@inheritDoc}
     */
    public InternalFreeze[] getFrozenChildNodes() throws VersionException {
        try {
            // maybe add iterator?
            List entries = node.getState().getChildNodeEntries();
            InternalFreeze[] freezes = new InternalFreeze[entries.size()];
            Iterator iter = entries.iterator();
            int i = 0;
            while (iter.hasNext()) {
                NodeState.ChildNodeEntry entry = (NodeState.ChildNodeEntry) iter.next();
                freezes[i++] = (InternalFreeze) vMgr.getItem(entry.getUUID());
            }
            return freezes;
        } catch (RepositoryException e) {
            throw new VersionException("Unable to retrieve frozen child nodes", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean hasFrozenHistory(String uuid) {
        try {
            NodeState.ChildNodeEntry entry  = node.getState().getChildNodeEntry(uuid);
            if (entry != null) {
                return vMgr.getItem(uuid) instanceof InternalFrozenVersionHistory;
            }
        } catch (RepositoryException e) {
            // ignore
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public PropertyState[] getFrozenProperties() {
        return frozenProperties;
    }

    /**
     * {@inheritDoc}
     */
    public String getFrozenUUID() {
        return frozenUUID;
    }

    /**
     * {@inheritDoc}
     */
    public QName getFrozenPrimaryType() {
        return frozenPrimaryType;
    }

    /**
     * {@inheritDoc}
     */
    public QName[] getFrozenMixinTypes() {
        return frozenMixinTypes;
    }

    /**
     * Checks-in a <code>src</code> node. It creates a new child node of
     * <code>parent</code> with the given <code>name</code> and adds the
     * source nodes properties according to their OPV value to the
     * list of frozen properties. It creates frozen child nodes for each child
     * node of <code>src</code> according to its OPV value.
     *
     * @param parent
     * @param name
     * @param src
     * @return
     * @throws RepositoryException
     */
    protected static NodeStateEx checkin(NodeStateEx parent, QName name,
                                            NodeImpl src)
            throws RepositoryException {
        return checkin(parent, name, src, MODE_VERSION);
    }

    /**
     * Checks-in a <code>src</code> node. It creates a new child node of
     * <code>parent</code> with the given <code>name</code> and adds the
     * source nodes properties according to their OPV value to the
     * list of frozen properties. It creates frozen child nodes for each child
     * node of <code>src</code> according to its OPV value.
     *
     * @param parent
     * @param name
     * @param src
     * @return
     * @throws RepositoryException
     */
    private static NodeStateEx checkin(NodeStateEx parent, QName name,
                                            NodeImpl src, int mode)
            throws RepositoryException {

        // create new node
        NodeStateEx node = parent.addNode(name, QName.NT_FROZENNODE, null, true);

        // initialize the internal properties
        node.setPropertyValue(QName.JCR_FROZENUUID, InternalValue.create(src.internalGetUUID()));
        node.setPropertyValue(QName.JCR_FROZENPRIMARYTYPE,
                InternalValue.create(((NodeTypeImpl) src.getPrimaryNodeType()).getQName()));
        if (src.hasProperty(QName.JCR_MIXINTYPES)) {
            NodeType[] mixins = src.getMixinNodeTypes();
            InternalValue[] ivalues = new InternalValue[mixins.length];
            for (int i = 0; i < mixins.length; i++) {
                ivalues[i] = InternalValue.create(((NodeTypeImpl) mixins[i]).getQName());
            }
            node.setPropertyValues(QName.JCR_FROZENMIXINTYPES, PropertyType.NAME, ivalues);
        }

        // add the properties
        PropertyIterator piter = src.getProperties();
        while (piter.hasNext()) {
            PropertyImpl prop = (PropertyImpl) piter.nextProperty();
            int opv;
            if ((mode & MODE_COPY) > 0) {
                opv = OnParentVersionAction.COPY;
            } else {
                opv = prop.getDefinition().getOnParentVersion();
            }
            switch (opv) {
                case OnParentVersionAction.ABORT:
                    parent.reload();
                    throw new VersionException("Checkin aborted due to OPV in " + prop.safeGetJCRPath());
                case OnParentVersionAction.COMPUTE:
                case OnParentVersionAction.IGNORE:
                case OnParentVersionAction.INITIALIZE:
                    break;
                case OnParentVersionAction.VERSION:
                case OnParentVersionAction.COPY:
                    node.copyFrom(prop);
                    break;
            }
        }

        // add the frozen children and histories
        NodeIterator niter = src.getNodes();
        while (niter.hasNext()) {
            NodeImpl child = (NodeImpl) niter.nextNode();
            int opv;
            if ((mode & MODE_COPY_RECURSIVE) > 0) {
                opv = OnParentVersionAction.COPY;
            } else {
                opv = child.getDefinition().getOnParentVersion();
            }
            switch (opv) {
                case OnParentVersionAction.ABORT:
                    throw new VersionException("Checkin aborted due to OPV in " + child.safeGetJCRPath());
                case OnParentVersionAction.COMPUTE:
                case OnParentVersionAction.IGNORE:
                case OnParentVersionAction.INITIALIZE:
                    break;
                case OnParentVersionAction.VERSION:
                    if (child.isNodeType(QName.MIX_VERSIONABLE)) {
                        // create frozen versionable child
                        NodeStateEx newChild = node.addNode(child.getQName(), QName.NT_VERSIONEDCHILD, null, false);
                        newChild.setPropertyValue(QName.JCR_CHILDVERSIONHISTORY,
                                InternalValue.create(new UUID(child.getVersionHistory().getUUID())));
                        /*
                        newChild.setPropertyValue(JCR_BASEVERSION,
                                InternalValue.create(child.getBaseVersion().getUUID()));
                        */
                        break;
                    }
                    // else copy but do not recurse
                    checkin(node, child.getQName(), child, MODE_COPY);
                    break;
                case OnParentVersionAction.COPY:
                    checkin(node, child.getQName(), child, MODE_COPY_RECURSIVE);
                    break;
            }
        }
        return node;
    }

}
