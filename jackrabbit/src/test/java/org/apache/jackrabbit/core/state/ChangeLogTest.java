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

import org.apache.jackrabbit.test.AbstractJCRTest;
import org.apache.jackrabbit.name.QName;

import java.util.Iterator;

/**
 * <code>ChangeLogTest</code> contains the test cases for the methods
 * inside {@link org.apache.jackrabbit.core.state.ChangeLog}.
 */
public class ChangeLogTest extends AbstractJCRTest {

    /**
     * Add an item state and then delete it. Make sure there is no
     * entry in either the added nor the removed states
     */
    public void testAddDelete() throws Exception {
        ItemState state = new PropertyState(new QName("", "a"), "",
                ItemState.STATUS_NEW, false);

        ChangeLog log = new ChangeLog();

        log.added(state);
        log.deleted(state);

        Iterator iter = log.addedStates();
        assertFalse("State not in added collection", iter.hasNext());
        iter = log.deletedStates();
        assertFalse("State not in deleted collection", iter.hasNext());
    }

    /**
     * Add an item state and then modify it. Make sure the entry is still
     * in the added states.
     */
    public void testAddModify() throws Exception {
        ItemState state = new PropertyState(new QName("", "a"), "",
                ItemState.STATUS_NEW, false);

        ChangeLog log = new ChangeLog();

        log.added(state);
        log.modified(state);

        Iterator iter = log.addedStates();
        assertTrue("State still in added collection", iter.hasNext());
        iter = log.modifiedStates();
        assertFalse("State not in modified collection", iter.hasNext());
    }

    /**
     * Add some item states. Retrieve them again and make sure the order is
     * preserved.
     */
    public void testPreserveOrder() throws Exception {
        ItemState[] states = new ItemState[10];
        for (int i = 0; i < states.length; i++) {
            states[i] = new PropertyState(new QName("", "a" + i), "",
                    ItemState.STATUS_NEW, false);
        }

        ChangeLog log = new ChangeLog();

        for (int i = 0; i < states.length; i++) {
            log.added(states[i]);
        }

        Iterator iter = log.addedStates();
        int i = 0;

        while (iter.hasNext()) {
            ItemState state = (ItemState) iter.next();
            assertTrue("Added states preserve order.",
                    state.equals(states[i++]));
        }
    }
}
