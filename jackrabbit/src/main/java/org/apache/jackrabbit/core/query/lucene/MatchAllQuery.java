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
package org.apache.jackrabbit.core.query.lucene;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.search.Weight;

/**
 * Specialized query that returns / scores all pages in the search index.
 * <p>Use this Query to perform a match '*'.
 */
class MatchAllQuery extends Query {

    private final String field;

    /**
     * Creates a new <code>MatchAllQuery</code> .
     * <p/>
     *
     * @param field the field name.
     * @throws NullPointerException if <code>field</code> is null.
     */
    MatchAllQuery(String field) throws NullPointerException {
        if (field == null) {
            throw new NullPointerException("field");
        }
        this.field = field.intern();
    }

    /**
     * Returns the <code>Weight</code> for this Query.
     *
     * @param searcher the current searcher.
     * @return the <code>Weight</code> for this Query.
     */
    protected Weight createWeight(Searcher searcher) {
        return new MatchAllWeight(this, searcher, field);
    }

    /**
     * Returns the String "%".
     *
     * @param field default field for the query.
     * @return the String "%".
     */
    public String toString(String field) {
        return "%";
    }
}
