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
package org.apache.jackrabbit.core.observation;

import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.state.ChangeLog;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * This Class implements an observation dispatcher, that delegates events to
 * a set of underlying dispatchers.
 */
public class DelegatingObservationDispatcher extends EventDispatcher {

    /**
     * the set of dispatchers
     */
    private final HashSet dispatchers = new HashSet();

    /**
     * Adds a new observation factory to the set of dispatchers
     *
     * @param disp
     */
    public void addDispatcher(ObservationManagerFactory disp) {
        dispatchers.add(disp);
    }

    /**
     * Removes a observation factory from the set of dispatchers
     *
     * @param disp
     */
    public void removeDispatcher(ObservationManagerFactory disp) {
        dispatchers.remove(disp);
    }

    /**
     * Creates an <code>EventStateCollection</code> tied to the session
     * given as argument.
     *
     * @param session event source
     * @return new <code>EventStateCollection</code> instance
     */
    public EventStateCollection createEventStateCollection(SessionImpl session) {
        return new EventStateCollection(this, session);
    }

    //------------------------------------------------------< EventDispatcher >

    /**
     * {@inheritDoc}
     */
    void prepareEvents(EventStateCollection events) {
        // events will get prepared on dispatch
    }

    /**
     * {@inheritDoc}
     */
    void prepareDeleted(EventStateCollection events, ChangeLog changes) {
        // events will get prepared on dispatch
    }

    /**
     * {@inheritDoc}
     */
    void dispatchEvents(EventStateCollection events) {
        dispatch(events.getEvents(), events.getSession());
    }

    /**
     * Dispatchers a list of events to all registered dispatchers. A new
     * {@link EventStateCollection} is created for every dispatcher, fille with
     * the given event list and then dispatched.
     *
     * @param eventList
     * @param session
     */
    public void dispatch(List eventList, SessionImpl session) {
        Iterator iter = dispatchers.iterator();
        while (iter.hasNext()) {
            ObservationManagerFactory fac = (ObservationManagerFactory) iter.next();
            EventStateCollection events = new EventStateCollection(fac, session);
            events.addAll(eventList);
            events.prepare();
            events.dispatch();
        }
    }
}
