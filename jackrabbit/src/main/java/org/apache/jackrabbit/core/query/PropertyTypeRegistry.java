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
package org.apache.jackrabbit.core.query;

import org.apache.jackrabbit.core.nodetype.ItemDef;
import org.apache.jackrabbit.core.nodetype.NodeTypeDef;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistry;
import org.apache.jackrabbit.core.nodetype.NodeTypeRegistryListener;
import org.apache.jackrabbit.core.nodetype.PropDef;
import org.apache.jackrabbit.name.QName;
import org.apache.log4j.Logger;

import javax.jcr.PropertyType;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The <code>PropertyTypeRegistry</code> keeps track of registered node type
 * definitions and its property types. It provides a fast type lookup for a
 * given property name.
 */
public class PropertyTypeRegistry implements NodeTypeRegistryListener {

    /** The logger instance for this class */
    private static final Logger log = Logger.getLogger(PropertyTypeRegistry.class);

    /**
     * Empty <code>TypeMapping</code> array as return value if no type is
     * found
     */
    private static final TypeMapping[] EMPTY = new TypeMapping[0];

    /** The NodeTypeRegistry */
    private final NodeTypeRegistry registry;

    /** Property QName to TypeMapping[] mapping */
    private final Map typeMapping = new HashMap();

    /**
     * Creates a new <code>PropertyTypeRegistry</code> instance. This instance
     * is *not* registered as listener to the NodeTypeRegistry in the constructor!
     * @param reg the <code>NodeTypeRegistry</code> where to read the property
     * type information.
     */
    public PropertyTypeRegistry(NodeTypeRegistry reg) {
        this.registry = reg;
        fillCache();
    }

    /**
     * Returns an array of type mappings for a given property name
     * <code>propName</code>. If <code>propName</code> is not defined as a property
     * in any registered node type an empty array is returned.
     * @param propName the name of the property.
     * @return an array of <code>TypeMapping</code> instances.
     */
    public TypeMapping[] getPropertyTypes(QName propName) {
        synchronized (typeMapping) {
            TypeMapping[] types = (TypeMapping[]) typeMapping.get(propName);
            if (types != null) {
                return types;
            } else {
                return EMPTY;
            }
        }
    }

    public void nodeTypeRegistered(QName ntName) {
        try {
            NodeTypeDef def = registry.getNodeTypeDef(ntName);
            PropDef[] propDefs = def.getPropertyDefs();
            synchronized (typeMapping) {
                for (int i = 0; i < propDefs.length; i++) {
                    int type = propDefs[i].getRequiredType();
                    if (!propDefs[i].definesResidual() && type != PropertyType.UNDEFINED) {
                        QName name = propDefs[i].getName();
                        // only remember defined property types
                        TypeMapping[] types = (TypeMapping[]) typeMapping.get(name);
                        if (types == null) {
                            types = new TypeMapping[1];
                        } else {
                            TypeMapping[] tmp = new TypeMapping[types.length + 1];
                            System.arraycopy(types, 0, tmp, 0, types.length);
                            types = tmp;
                        }
                        types[types.length - 1] = new TypeMapping(type, ntName);
                        typeMapping.put(name, types);
                    }
                }
            }
        } catch (NoSuchNodeTypeException e) {
            log.error("Unable to get newly registered node type definition for name: " + ntName);
        }
    }

    public void nodeTypeReRegistered(QName ntName) {
        nodeTypeUnregistered(ntName);
        nodeTypeRegistered(ntName);
    }

    public void nodeTypeUnregistered(QName ntName) {
        // remove all TypeMapping instances refering to this ntName
        synchronized (typeMapping) {
            Map modified = new HashMap();
            for (Iterator it = typeMapping.keySet().iterator(); it.hasNext();) {
                QName propName = (QName) it.next();
                TypeMapping[] mapping = (TypeMapping[]) typeMapping.get(propName);
                List remove = null;
                for (int i = 0; i < mapping.length; i++) {
                    if (mapping[i].ntName.equals(ntName)) {
                        if (remove == null) {
                            // not yet created
                            remove = new ArrayList(mapping.length);
                        }
                        remove.add(mapping[i]);
                    }
                }
                if (remove != null) {
                    it.remove();
                    if (mapping.length == remove.size()) {
                        // all removed -> done
                    } else {
                        // only some removed
                        List remaining = new ArrayList(Arrays.asList(mapping));
                        remaining.removeAll(remove);
                        modified.put(propName, remaining.toArray(new TypeMapping[remaining.size()]));
                    }
                }
            }
            // finally re-add the modified mappings
            typeMapping.putAll(modified);
        }
    }

    /**
     * Initially fills the cache of this registry with property type definitions
     * from the {@link org.apache.jackrabbit.core.nodetype.NodeTypeRegistry}.
     */
    private void fillCache() {
        QName[] ntNames = registry.getRegisteredNodeTypes();
        for (int i = 0; i < ntNames.length; i++) {
            nodeTypeRegistered(ntNames[i]);
        }
    }

    public static class TypeMapping {

        /** The property type as an integer */
        public final int type;

        /** The QName of the node type where this type mapping originated */
        final QName ntName;

        private TypeMapping(int type, QName ntName) {
            this.type = type;
            this.ntName = ntName;
        }
    }
}
