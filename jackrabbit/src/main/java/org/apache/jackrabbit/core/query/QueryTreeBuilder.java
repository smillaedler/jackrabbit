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

import org.apache.jackrabbit.name.NamespaceResolver;

import javax.jcr.query.InvalidQueryException;

/**
 * Specifies an interface for a query tree builder.
 */
public interface QueryTreeBuilder {

    /**
     * Creates a <code>QueryNode</code> tree from a statement.
     *
     * @param statement the statement.
     * @param resolver  the namespace resolver to use.
     * @return the <code>QueryNode</code> tree for the statement.
     * @throws javax.jcr.query.InvalidQueryException
     *          if the statement is malformed.
     */
    public QueryRootNode createQueryTree(String statement, NamespaceResolver resolver)
            throws InvalidQueryException;

    /**
     * Returns <code>true</code> if this query tree builder can handle a
     * statement in <code>language</code>.
     *
     * @param language the language of a query statement to build a query tree.
     * @return <code>true</code> if this builder can handle <code>language</code>;
     *         <code>false</code> otherwise.
     */
    public boolean canHandle(String language);

    /**
     * Creates a String representation of the query node tree in the syntax this
     * <code>QueryTreeBuilder</code> can handle.
     *
     * @param root     the root of the query node tree.
     * @param resolver to resolve QNames.
     * @return a String representation of the query node tree.
     * @throws InvalidQueryException if the query node tree cannot be converted
     *                               into a String representation due to
     *                               restrictions in this syntax.
     */
    public String toString(QueryRootNode root, NamespaceResolver resolver)
            throws InvalidQueryException;
}
