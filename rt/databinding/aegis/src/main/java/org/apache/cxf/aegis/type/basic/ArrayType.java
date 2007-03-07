/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.aegis.type.basic;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.aegis.Aegis;
import org.apache.cxf.aegis.Context;
import org.apache.cxf.aegis.DatabindingException;
import org.apache.cxf.aegis.type.Type;
import org.apache.cxf.aegis.util.NamespaceHelper;
import org.apache.cxf.aegis.util.XmlConstants;
import org.apache.cxf.aegis.xml.MessageReader;
import org.apache.cxf.aegis.xml.MessageWriter;
import org.jdom.Attribute;
import org.jdom.Element;

/**
 * An ArrayType.
 * 
 * @author <a href="mailto:dan@envoisolutions.com">Dan Diephouse</a>
 */
public class ArrayType extends Type {
    private QName componentName;
    private static final Log logger = LogFactory.getLog(ArrayType.class);
    private long minOccurs = 0;
    private long maxOccurs = Long.MAX_VALUE;
    private boolean flat;

    public ArrayType() {
    }

    @Override
    public Object readObject(MessageReader reader, Context context) throws DatabindingException {
        try {
            Collection values = readCollection(reader, context);

            return makeArray(getComponentType().getTypeClass(), values);
        } catch (IllegalArgumentException e) {
            throw new DatabindingException("Illegal argument.", e);
        }
    }

    protected Collection<Object> createCollection() {
        return new ArrayList<Object>();
    }

    protected Collection readCollection(MessageReader reader, Context context) throws DatabindingException {
        Collection<Object> values = createCollection();

        while (reader.hasMoreElementReaders()) {
            MessageReader creader = reader.getNextElementReader();
            Type compType = Aegis.getReadType(creader.getXMLStreamReader(), context,
                                                             getComponentType());

            if (creader.isXsiNil()) {
                values.add(null);
                creader.readToEnd();
            } else {
                values.add(compType.readObject(creader, context));
            }

            // check max occurs
            int size = values.size();
            if (size > maxOccurs) {
                throw new DatabindingException("The number of elements in " + getSchemaType()
                                               + " exceeds the maximum of " + maxOccurs);
            }

        }

        // check min occurs
        if (values.size() < minOccurs) {
            throw new DatabindingException("The number of elements in " + getSchemaType()
                                           + " does not meet the minimum of " + minOccurs);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    protected Object makeArray(Class arrayType, Collection values) {
        if (Integer.TYPE.equals(arrayType)) {
            Object[] objects = values.toArray();
            Object array = Array.newInstance(Integer.TYPE, objects.length);
            for (int i = 0, n = objects.length; i < n; i++) {
                Array.set(array, i, objects[i]);
            }
            return array;
        } else if (Long.TYPE.equals(arrayType)) {
            Object[] objects = values.toArray();
            Object array = Array.newInstance(Long.TYPE, objects.length);
            for (int i = 0, n = objects.length; i < n; i++) {
                Array.set(array, i, objects[i]);
            }
            return array;
        } else if (Short.TYPE.equals(arrayType)) {
            Object[] objects = values.toArray();
            Object array = Array.newInstance(Short.TYPE, objects.length);
            for (int i = 0, n = objects.length; i < n; i++) {
                Array.set(array, i, objects[i]);
            }
            return array;
        } else if (Double.TYPE.equals(arrayType)) {
            Object[] objects = values.toArray();
            Object array = Array.newInstance(Double.TYPE, objects.length);
            for (int i = 0, n = objects.length; i < n; i++) {
                Array.set(array, i, objects[i]);
            }
            return array;
        } else if (Float.TYPE.equals(arrayType)) {
            Object[] objects = values.toArray();
            Object array = Array.newInstance(Float.TYPE, objects.length);
            for (int i = 0, n = objects.length; i < n; i++) {
                Array.set(array, i, objects[i]);
            }
            return array;
        } else if (Byte.TYPE.equals(arrayType)) {
            Object[] objects = values.toArray();
            Object array = Array.newInstance(Byte.TYPE, objects.length);
            for (int i = 0, n = objects.length; i < n; i++) {
                Array.set(array, i, objects[i]);
            }
            return array;
        } else if (Boolean.TYPE.equals(arrayType)) {
            Object[] objects = values.toArray();
            Object array = Array.newInstance(Boolean.TYPE, objects.length);
            for (int i = 0, n = objects.length; i < n; i++) {
                Array.set(array, i, objects[i]);
            }
            return array;
        } else if (Character.TYPE.equals(arrayType)) {
            Object[] objects = values.toArray();
            Object array = Array.newInstance(Character.TYPE, objects.length);
            for (int i = 0, n = objects.length; i < n; i++) {
                Array.set(array, i, objects[i]);
            }
            return array;
        }
        return values.toArray((Object[])Array.newInstance(getComponentType().getTypeClass(), values.size()));
    }

    @Override
    public void writeObject(Object values, MessageWriter writer, Context context) throws DatabindingException {
        if (values == null) {
            return;
        }

        Type type = getComponentType();

        String ns = null;
        if (type.isAbstract()) {
            ns = getSchemaType().getNamespaceURI();
        } else {
            ns = type.getSchemaType().getNamespaceURI();
        }

        String name = type.getSchemaType().getLocalPart();

        if (type == null) {
            throw new DatabindingException("Couldn't find type for " + type.getTypeClass() + ".");
        }

        Class arrayType = type.getTypeClass();

        if (Object.class.isAssignableFrom(arrayType)) {
            Object[] objects = (Object[])values;
            for (int i = 0, n = objects.length; i < n; i++) {
                writeValue(objects[i], writer, context, type, name, ns);
            }
        } else if (Integer.TYPE.equals(arrayType)) {
            int[] objects = (int[])values;
            for (int i = 0, n = objects.length; i < n; i++) {
                writeValue(new Integer(objects[i]), writer, context, type, name, ns);
            }
        } else if (Long.TYPE.equals(arrayType)) {
            long[] objects = (long[])values;
            for (int i = 0, n = objects.length; i < n; i++) {
                writeValue(new Long(objects[i]), writer, context, type, name, ns);
            }
        } else if (Short.TYPE.equals(arrayType)) {
            short[] objects = (short[])values;
            for (int i = 0, n = objects.length; i < n; i++) {
                writeValue(new Short(objects[i]), writer, context, type, name, ns);
            }
        } else if (Double.TYPE.equals(arrayType)) {
            double[] objects = (double[])values;
            for (int i = 0, n = objects.length; i < n; i++) {
                writeValue(new Double(objects[i]), writer, context, type, name, ns);
            }
        } else if (Float.TYPE.equals(arrayType)) {
            float[] objects = (float[])values;
            for (int i = 0, n = objects.length; i < n; i++) {
                writeValue(new Float(objects[i]), writer, context, type, name, ns);
            }
        } else if (Byte.TYPE.equals(arrayType)) {
            byte[] objects = (byte[])values;
            for (int i = 0, n = objects.length; i < n; i++) {
                writeValue(new Byte(objects[i]), writer, context, type, name, ns);
            }
        } else if (Boolean.TYPE.equals(arrayType)) {
            boolean[] objects = (boolean[])values;
            for (int i = 0, n = objects.length; i < n; i++) {
                writeValue(new Boolean(objects[i]), writer, context, type, name, ns);
            }
        } else if (Character.TYPE.equals(arrayType)) {
            char[] objects = (char[])values;
            for (int i = 0, n = objects.length; i < n; i++) {
                writeValue(new Character(objects[i]), writer, context, type, name, ns);
            }
        }
    }

    protected void writeValue(Object value, MessageWriter writer, Context context, Type type, String name,
                              String ns) throws DatabindingException {
        type = Aegis.getWriteType(context, value, type);
        MessageWriter cwriter = writer.getElementWriter(name, ns);

        if (value == null && type.isNillable()) {
            cwriter.writeXsiNil();
        } else {
            type.writeObject(value, cwriter, context);
        }

        cwriter.close();
    }

    @Override
    public void writeSchema(Element root) {
        try {
            Element complex = new Element("complexType", XmlConstants.XSD_PREFIX, XmlConstants.XSD);
            complex.setAttribute(new Attribute("name", getSchemaType().getLocalPart()));
            root.addContent(complex);

            Element seq = new Element("sequence", XmlConstants.XSD_PREFIX, XmlConstants.XSD);
            complex.addContent(seq);

            Element element = new Element("element", XmlConstants.XSD_PREFIX, XmlConstants.XSD);
            seq.addContent(element);

            Type componentType = getComponentType();
            String prefix = NamespaceHelper.getUniquePrefix(root, componentType
                .getSchemaType().getNamespaceURI());

            String typeName = prefix + ":" + componentType.getSchemaType().getLocalPart();

            element.setAttribute(new Attribute("name", componentType.getSchemaType().getLocalPart()));
            element.setAttribute(new Attribute("type", typeName));

            if (componentType.isNillable()) {
                element.setAttribute(new Attribute("nillable", "true"));
            }

            element.setAttribute(new Attribute("minOccurs", new Long(getMinOccurs()).toString()));

            if (maxOccurs == Long.MAX_VALUE) {
                element.setAttribute(new Attribute("maxOccurs", "unbounded"));
            } else {
                element.setAttribute(new Attribute("maxOccurs", new Long(getMaxOccurs()).toString()));
            }

        } catch (IllegalArgumentException e) {
            throw new DatabindingException("Illegal argument.", e);
        }
    }

    /**
     * We need to write a complex type schema for Beans, so return true.
     * 
     * @see org.apache.cxf.aegis.type.Type#isComplex()
     */
    @Override
    public boolean isComplex() {
        return true;
    }

    public QName getComponentName() {
        return componentName;
    }

    public void setComponentName(QName componentName) {
        this.componentName = componentName;
    }

    /**
     * @see org.apache.cxf.aegis.type.Type#getDependencies()
     */
    @Override
    public Set<Type> getDependencies() {
        Set<Type> deps = new HashSet<Type>();

        deps.add(getComponentType());

        return deps;
    }

    /**
     * Get the <code>Type</code> of the elements in the array.
     * 
     * @return
     */
    public Type getComponentType() {
        Class compType = getTypeClass().getComponentType();

        Type type;

        if (componentName == null) {
            type = getTypeMapping().getType(compType);
        } else {
            type = getTypeMapping().getType(componentName);

            // We couldn't find the type the user specified. One is created
            // below instead.
            if (type == null) {
                logger.debug("Couldn't find array component type " + componentName
                             + ". Creating one instead.");
            }
        }

        if (type == null) {
            type = getTypeMapping().getTypeCreator().createType(compType);
            getTypeMapping().register(type);
        }

        return type;
    }

    public long getMaxOccurs() {
        return maxOccurs;
    }

    public void setMaxOccurs(long maxOccurs) {
        this.maxOccurs = maxOccurs;
    }

    public long getMinOccurs() {
        return minOccurs;
    }

    public void setMinOccurs(long minOccurs) {
        this.minOccurs = minOccurs;
    }

    public boolean isFlat() {
        return flat;
    }

    public void setFlat(boolean flat) {
        setWriteOuter(!flat);
        this.flat = flat;
    }
}
