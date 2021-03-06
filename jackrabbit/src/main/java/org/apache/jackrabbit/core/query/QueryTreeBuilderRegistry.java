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

import org.apache.log4j.Logger;

import javax.imageio.spi.ServiceRegistry;
import javax.jcr.query.InvalidQueryException;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * Implements a central access to QueryTreeBuilder instances.
 */
public class QueryTreeBuilderRegistry {

    /**
     * Logger instance for this class.
     */
    private static final Logger log = Logger.getLogger(QueryTreeBuilderRegistry.class);

    /**
     * List of <code>QueryTreeBuilder</code> instances known to the classloader.
     */
    private static final List BUILDERS = new ArrayList();

    static {
        try {
            Iterator it = ServiceRegistry.lookupProviders(QueryTreeBuilder.class, 
                    QueryTreeBuilderRegistry.class.getClassLoader());
            while (it.hasNext()) {
                BUILDERS.add(it.next());
            }
        } catch (Error e) {
            log.warn("Unable to load providers for QueryTreeBuilder: " + e);
        }
    }

    /**
     * Returns the <code>QueryTreeBuilder</code> for <code>language</code>.
     *
     * @param language the language of the query statement.
     * @return the <code>QueryTreeBuilder</code> for <code>language</code>.
     * @throws InvalidQueryException if there is no query tree builder for
     *                               <code>language</code>.
     */
    public static QueryTreeBuilder getQueryTreeBuilder(String language)
            throws InvalidQueryException {
        for (int i = 0; i < BUILDERS.size(); i++) {
            QueryTreeBuilder builder = (QueryTreeBuilder) BUILDERS.get(i);
            if (builder.canHandle(language)) {
                return builder;
            }
        }
        throw new InvalidQueryException("Unsupported language: " + language);
    }
}
