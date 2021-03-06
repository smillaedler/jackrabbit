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

/**
 * Implements the <code>QueryNodeVisitor</code> interface with default behaviour.
 * All methods are no-ops and return the <code>data</code> argument.
 */
public class DefaultQueryNodeVisitor implements QueryNodeVisitor {

    public Object visit(QueryRootNode node, Object data) {
        return data;
    }

    public Object visit(OrQueryNode node, Object data) {
        return data;
    }

    public Object visit(AndQueryNode node, Object data) {
        return data;
    }

    public Object visit(NotQueryNode node, Object data) {
        return data;
    }

    public Object visit(ExactQueryNode node, Object data) {
        return data;
    }

    public Object visit(NodeTypeQueryNode node, Object data) {
        return data;
    }

    public Object visit(TextsearchQueryNode node, Object data) {
        return data;
    }

    public Object visit(PathQueryNode node, Object data) {
        return data;
    }

    public Object visit(LocationStepQueryNode node, Object data) {
        return data;
    }

    public Object visit(RelationQueryNode node, Object data) {
        return data;
    }

    public Object visit(OrderQueryNode node, Object data) {
        return data;
    }

    public Object visit(DerefQueryNode node, Object data) {
        return data;
    }
}
