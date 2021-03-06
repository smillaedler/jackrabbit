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
package org.apache.jackrabbit.core.security;

import java.io.Serializable;
import java.security.Principal;

/**
 * A <code>SystemPrincipal</code> ...
 */
public class SystemPrincipal implements Principal, Serializable {

    private static final String SYSTEM_USER = "system";

    /**
     * Creates a <code>SystemPrincipal</code>.
     */
    public SystemPrincipal() {
    }

    public String toString() {
        return ("SystemPrincipal");
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof SystemPrincipal) {
            return true;
        }
        return false;
    }

    public int hashCode() {
        return SYSTEM_USER.hashCode();
    }

    //------------------------------------------------------------< Principal >
    /**
     * {@inheritDoc}
     */
    public String getName() {
        return SYSTEM_USER;
    }
}
