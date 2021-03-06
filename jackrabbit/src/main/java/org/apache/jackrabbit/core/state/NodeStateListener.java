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
package org.apache.jackrabbit.core.state;

import org.apache.jackrabbit.name.QName;

/**
 * Extends the <code>ItemStateListener</code> allowing a client to be
 * additionally informed about changes on a <code>NodeState</code>.
 *
 * @see NodeState#addListener
 */
public interface NodeStateListener extends ItemStateListener {

    /**
     * Called when a child node has been added
     *
     * @param state node state that changed
     * @param name  name of node that was added
     * @param index index of new node
     * @param uuid  uuid of new node
     */
    void nodeAdded(NodeState state,
                   QName name, int index, String uuid);

    /**
     * Called when the children nodes were replaced by other nodes, typically
     * as result of a reorder operation.
     *
     * @param state node state that changed
     */
    void nodesReplaced(NodeState state);

    /**
     * Called when a child node has been removed
     *
     * @param state node state that changed
     * @param name  name of node that was removed
     * @param index index of removed node
     * @param uuid  uuid of removed node
     */
    public void nodeRemoved(NodeState state,
                            QName name, int index, String uuid);
}
