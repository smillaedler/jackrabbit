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
package org.apache.jackrabbit.core.virtual;

import org.apache.jackrabbit.core.state.ItemState;
import org.apache.jackrabbit.core.state.PropertyState;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.name.QName;

/**
 * This Class implements a virtual property state
 */
public class VirtualPropertyState extends PropertyState {

    /**
     * a virtual value provider, if needed.
     */
    private VirtualValueProvider valueProvider;

    /**
     * Creates a new virtual property state
     * @param name
     * @param parentUUID
     */
    public VirtualPropertyState(QName name, String parentUUID) {
        super(name, parentUUID, ItemState.STATUS_EXISTING, false);
    }

    /**
     * Returns the virtual value provider, if registered.
     * @return
     */
    public VirtualValueProvider getValueProvider() {
        return valueProvider;
    }

    /**
     * Sets a virtual value provider for this property
     * @param valueProvider
     */
    public void setValueProvider(VirtualValueProvider valueProvider) {
        this.valueProvider = valueProvider;
    }

    /**
     * Returns the value of this state evt. by using the registered virtual
     * value provider.
     * @return
     */
    public InternalValue[] getValues() {
        InternalValue[] values = null;
        if (valueProvider != null) {
            values = valueProvider.getVirtualValues(name);
        }
        return valueProvider == null ? super.getValues() : values;
    }
}
