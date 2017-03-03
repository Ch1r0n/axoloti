/**
 * Copyright (C) 2013 - 2016 Johannes Taelman
 *
 * This file is part of Axoloti.
 *
 * Axoloti is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * Axoloti is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Axoloti. If not, see <http://www.gnu.org/licenses/>.
 */
package axoloti.attribute;

import axoloti.attributedefinition.AxoAttribute;
import axoloti.attributeviews.IAttributeInstanceView;
import axoloti.object.AxoObjectInstance;
import axoloti.objectviews.IAxoObjectInstanceView;
import org.simpleframework.xml.Attribute;

/**
 *
 * @author Johannes Taelman
 */
public abstract class AttributeInstanceInt<T extends AxoAttribute> extends AttributeInstance<T> {

    @Attribute
    int value;

    private int valueBeforeAdjustment;

    public AttributeInstanceInt() {
    }

    public AttributeInstanceInt(T param, AxoObjectInstance axoObj1) {
        super(param, axoObj1);
    }

    @Override
    public void CopyValueFrom(AttributeInstance a) {
        if (a instanceof AttributeInstanceInt) {
            AttributeInstanceInt a1 = (AttributeInstanceInt) a;
            value = a1.value;
        }
    }

    @Override
    public IAttributeInstanceView getViewInstance(IAxoObjectInstanceView o) {
        throw new RuntimeException("Cannot instantiate AttributeInstanceIntView");
    }

    public int getValueBeforeAdjustment() {
        return valueBeforeAdjustment;
    }

    public void setValueBeforeAdjustment(int valueBeforeAdjustment) {
        this.valueBeforeAdjustment = valueBeforeAdjustment;
    }
}
