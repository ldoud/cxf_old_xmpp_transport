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

package org.apache.cxf.jaxrs.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.Providers;

import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.PrimitiveUtils;
import org.apache.cxf.common.util.ReflectionUtil;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.ext.ProtocolHeaders;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.impl.PathSegmentImpl;
import org.apache.cxf.jaxrs.impl.tl.ThreadLocalContextResolver;
import org.apache.cxf.jaxrs.impl.tl.ThreadLocalHttpHeaders;
import org.apache.cxf.jaxrs.impl.tl.ThreadLocalInvocationHandler;
import org.apache.cxf.jaxrs.impl.tl.ThreadLocalMessageContext;
import org.apache.cxf.jaxrs.impl.tl.ThreadLocalProtocolHeaders;
import org.apache.cxf.jaxrs.impl.tl.ThreadLocalProviders;
import org.apache.cxf.jaxrs.impl.tl.ThreadLocalProxy;
import org.apache.cxf.jaxrs.impl.tl.ThreadLocalRequest;
import org.apache.cxf.jaxrs.impl.tl.ThreadLocalSecurityContext;
import org.apache.cxf.jaxrs.impl.tl.ThreadLocalUriInfo;
import org.apache.cxf.jaxrs.model.AbstractResourceInfo;
import org.apache.cxf.jaxrs.model.Parameter;
import org.apache.cxf.jaxrs.model.ParameterType;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;

public final class InjectionUtils {
    
    private static final Logger LOG = LogUtils.getL7dLogger(InjectionUtils.class);
    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(InjectionUtils.class);

    private static final String SERVLET_CONFIG_CLASS_NAME = "javax.servlet.ServletConfig";
    private static final String SERVLET_CONTEXT_CLASS_NAME = "javax.servlet.ServletContext";
    private static final String HTTP_SERVLET_REQUEST_CLASS_NAME = "javax.servlet.http.HttpServletRequest";
    private static final String HTTP_SERVLET_RESPONSE_CLASS_NAME = "javax.servlet.http.HttpServletResponse";
        
    private static final String PARAM_HANDLERS_FIRST = "check.parameter.handlers.first";
    private static final String IGNORE_MATRIX_PARAMETERS = "ignore.matrix.parameters";
    
    private InjectionUtils() {
        
    }

    public static boolean isConcreteClass(Class<?> cls) {
        return !cls.isInterface() && !Modifier.isAbstract(cls.getModifiers());
    }
    
    public static Type getSuperType(Class<?> serviceClass, TypeVariable<?> var) {
        
        int pos = 0;
        TypeVariable<?>[] vars = var.getGenericDeclaration().getTypeParameters();
        for (; pos < vars.length; pos++) {
            if (vars[pos].getName().equals(var.getName())) {
                break;
            }
        }
        
        Type[] bounds = var.getBounds();
        int boundPos = bounds.length > pos ? pos : 0; 
        if (bounds.length > boundPos && bounds[boundPos] != Object.class) {
            return bounds[boundPos];
        }
                
        Type genericSubtype = serviceClass.getGenericSuperclass();
        if (genericSubtype == Object.class) {
            Type[] genInterfaces = serviceClass.getGenericInterfaces();
            for (Type t : genInterfaces) {
                genericSubtype = t;
                break;
            }
        }
        Type result = genericSubtype != Object.class ? InjectionUtils.getActualType(genericSubtype, pos)
                                              : genericSubtype;
        return result == null ? Object.class : result;
    }
    
    public static Method checkProxy(Method methodToInvoke, Object resourceObject) {
        if (Proxy.class.isInstance(resourceObject)) {
            for (Class<?> c : resourceObject.getClass().getInterfaces()) {
                try {
                    Method m = c.getMethod(
                        methodToInvoke.getName(), methodToInvoke.getParameterTypes());
                    if (m != null) {
                        return m;
                    }
                } catch (NoSuchMethodException ex) {
                    //ignore
                }
            }
        }
        return methodToInvoke; 
    }
 
    public static void injectFieldValue(final Field f, 
                                        final Object o, 
                                        final Object v) {
        ReflectionUtil.setAccessible(f);
        try {
            f.set(o, v);
        } catch (IllegalAccessException ex) {
            reportServerError("FIELD_ACCESS_FAILURE", 
                              f.getType().getName());
        }
    }

    public static Object extractFieldValue(final Field f, 
                                        final Object o) {
        return AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                f.setAccessible(true);
                try {
                    return f.get(o);
                } catch (IllegalAccessException ex) {
                    reportServerError("FIELD_ACCESS_FAILURE", 
                                      f.getType().getName());
                }
                return null;
            }
        });
    }
    
    public static Class<?> getActualType(Type genericType) {
        
        return getActualType(genericType, 0);
    }
    
    public static Class<?> getActualType(Type genericType, int pos) {
        
        if (genericType == null) {
            return null;
        }
        if (!ParameterizedType.class.isAssignableFrom(genericType.getClass())) {
            if (genericType instanceof TypeVariable) {
                genericType = getType(((TypeVariable<?>)genericType).getBounds(), pos);
            } else if (genericType instanceof WildcardType) { 
                WildcardType wildcardType = (WildcardType)genericType;
                Type[] bounds = wildcardType.getLowerBounds();
                if (bounds.length == 0) { 
                    bounds = wildcardType.getUpperBounds();
                }
                genericType = getType(bounds, pos);
            }

            Class<?> cls = (Class<?>)genericType;
            return cls.isArray() ? cls.getComponentType() : cls;
        }
        ParameterizedType paramType = (ParameterizedType)genericType;
        Type t = getType(paramType.getActualTypeArguments(), pos);
        return t instanceof Class ? (Class<?>)t : getActualType(t, pos);
    }
    
    public static Type getType(Type[] types, int pos) {
        if (pos >= types.length) {
            throw new RuntimeException("No type can be found at position " + pos);
        }
        return types[pos];    
    }
    
    public static Class<?> getRawType(Type genericType) {
        
        if (genericType == null) {
            return null;
        } else if (genericType instanceof Class) {
            return (Class<?>) genericType;
        } else if (genericType instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType)genericType;
            Type t = paramType.getRawType();
            if (t instanceof Class) {
                return (Class<?>)t;
            }
        }
        // it might be a TypeVariable, or a GenericArray.
        return null;
    }
    
    
    public static Type[] getActualTypes(Type genericType) {
        if (genericType == null 
            || !ParameterizedType.class.isAssignableFrom(genericType.getClass())) {
            return null;
        }
        ParameterizedType paramType = (ParameterizedType)genericType;
        return paramType.getActualTypeArguments();
    }
    
    public static void injectThroughMethod(Object requestObject,
                                           Method method,
                                           Object parameterValue) {
        try {
            Method methodToInvoke = checkProxy(method, requestObject);
            methodToInvoke.invoke(requestObject, new Object[]{parameterValue});
        } catch (IllegalAccessException ex) {
            reportServerError("METHOD_ACCESS_FAILURE", method.getName());
        } catch (Exception ex) {
            reportServerError("METHOD_INJECTION_FAILURE", method.getName());
        }
    }

    public static Object extractFromMethod(Object requestObject,
                                           Method method) {
        try {
            Method methodToInvoke = checkProxy(method, requestObject);
            return methodToInvoke.invoke(requestObject);
        } catch (IllegalAccessException ex) {
            reportServerError("METHOD_ACCESS_FAILURE", method.getName());
        } catch (Exception ex) {
            reportServerError("METHOD_INJECTION_FAILURE", method.getName());
        }
        return null;
    }
    
    public static <T> T handleParameter(String value, 
                                        boolean decoded,
                                        Class<T> pClass,
                                        Annotation[] paramAnns,
                                        ParameterType pType,
                                        Message message) {
        if (value == null) {
            return null;
        }
        if (pType == ParameterType.PATH) {
            if (PathSegment.class.isAssignableFrom(pClass)) {
                return pClass.cast(new PathSegmentImpl(value, decoded));   
            } else if (!MessageUtils.isTrue(
                        message.getContextualProperty(IGNORE_MATRIX_PARAMETERS))) {
                value = new PathSegmentImpl(value, false).getPath();    
            }
        }
        
        value = decodeValue(value, decoded, pType);
        
        if (pClass.isPrimitive()) {
            try {
                @SuppressWarnings("unchecked")
                T ret = (T)PrimitiveUtils.read(value, pClass);
                // cannot us pClass.cast as the pClass is something like
                // Boolean.TYPE (representing the boolean primitive) and
                // the object is a Boolean object
                return ret;
            } catch (NumberFormatException nfe) {
                //
                //  For path, query & matrix parameters this is 404,
                //  for others 400...
                //
                if (pType == ParameterType.PATH || pType == ParameterType.QUERY
                    || pType == ParameterType.MATRIX) {
                    throw new NotFoundException(nfe);
                }
                throw new BadRequestException(nfe);
            }
        }
        
        boolean adapterHasToBeUsed = false;
        Class<?> cls = pClass;        
        Class<?> valueType = JAXBUtils.getValueTypeFromAdapter(pClass, pClass, paramAnns);
        if (valueType != cls) {
            cls = valueType;
            adapterHasToBeUsed = true;
        }
        
        Object result = instantiateFromParameterHandler(value, cls, message);
        if (result != null) {
            return pClass.cast(result);
        }
        // check constructors accepting a single String value
        try {
            Constructor<?> c = cls.getConstructor(new Class<?>[]{String.class});
            result = c.newInstance(new Object[]{value});
        } catch (NoSuchMethodException ex) {
            // try valueOf
        } catch (WebApplicationException ex) {
            throw ex;
        } catch (Exception ex) {
            result = createFromParameterHandler(value, cls, message);
            if (result == null) {
                LOG.severe(new org.apache.cxf.common.i18n.Message("CLASS_CONSTRUCTOR_FAILURE", 
                                                                   BUNDLE, 
                                                                   pClass.getName()).toString());
                throw new ServerErrorException(HttpUtils.getParameterFailureStatus(pType), ex);
            }
        }
        if (result == null) {
            // check for valueOf(String) static methods
            String[] methodNames = cls.isEnum() 
                ? new String[] {"fromString", "fromValue", "valueOf"} 
                : new String[] {"valueOf", "fromString"};
            for (String mName : methodNames) {   
                result = evaluateFactoryMethod(value, cls, pType, mName);
                if (result != null) {
                    break;
                }
            }
        }
        
        if (result == null) {
            result = createFromParameterHandler(value, cls, message);
        }
        
        if (adapterHasToBeUsed) {
            // as the last resort, try XmlJavaTypeAdapters
            Object valueToReplace = result != null ? result : value;
            try {
                result = JAXBUtils.convertWithAdapter(valueToReplace, pClass, paramAnns);
            } catch (Throwable ex) {
                result = null; 
            }
        }
        
        if (result == null) {
            reportServerError("WRONG_PARAMETER_TYPE", pClass.getName());
        }
        
        return pClass.cast(result);
    }

    private static <T> T instantiateFromParameterHandler(String value, 
                                                     Class<T> pClass,
                                                     Message m) {
        if (Date.class == pClass || Locale.class == pClass 
            || m != null && MessageUtils.isTrue(m.getContextualProperty(PARAM_HANDLERS_FIRST))) {
            return createFromParameterHandler(value, pClass, m);
        } else {
            return null;
        }
    }
    
    private static <T> T createFromParameterHandler(String value, 
                                                    Class<T> pClass,
                                                    Message message) {
        T result = null;
        if (message != null) {
            ParamConverter<T> pm = ProviderFactory.getInstance(message)
                .createParameterHandler(pClass);
            if (pm != null) {
                result = pm.fromString(value);
            }
        }
        return result;
    }
    
    public static void reportServerError(String messageName, String parameter) {
        org.apache.cxf.common.i18n.Message errorMessage = 
            new org.apache.cxf.common.i18n.Message(messageName, 
                                                   BUNDLE, 
                                                   parameter);
        LOG.severe(errorMessage.toString());
        Response r = Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                         .type(MediaType.TEXT_PLAIN_TYPE)
                         .entity(errorMessage.toString()).build();
        throw new InternalServerErrorException(r);
    }
    
    private static <T> T evaluateFactoryMethod(String value,
                                                Class<T> pClass, 
                                                ParameterType pType, 
                                                String methodName) {
        try {
            Method m = pClass.getMethod(methodName, new Class<?>[]{String.class});
            if (Modifier.isStatic(m.getModifiers())) {
                return pClass.cast(m.invoke(null, new Object[]{value}));
            }
        } catch (NoSuchMethodException ex) {
            // no luck
        } catch (Exception ex) {
            Throwable t = ex instanceof InvocationTargetException 
                ? ((InvocationTargetException)ex).getTargetException() : ex; 
            LOG.severe(new org.apache.cxf.common.i18n.Message("CLASS_VALUE_OF_FAILURE", 
                                                               BUNDLE, 
                                                               pClass.getName()).toString());
            throw new WebApplicationException(t, HttpUtils.getParameterFailureStatus(pType));
        }
        return null;
    }
    
    public static Object handleBean(Class<?> paramType, Annotation[] paramAnns, 
                                    MultivaluedMap<String, String> values,
                                    ParameterType pType, Message message, boolean decoded) {
        Object bean = null;
        try {
            if (paramType.isInterface()) {
                paramType = org.apache.cxf.jaxrs.utils.JAXBUtils.getValueTypeFromAdapter(paramType,
                                                                                         paramType, 
                                                                                         paramAnns);
            }
            bean = paramType.newInstance();
        } catch (IllegalAccessException ex) {
            reportServerError("CLASS_ACCESS_FAILURE", paramType.getName());
        } catch (Exception ex) {
            reportServerError("CLASS_INSTANTIATION_FAILURE", paramType.getName());
        }    
        
        Map<String, MultivaluedMap<String, String>> parsedValues =
            new HashMap<String, MultivaluedMap<String, String>>();
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            String memberKey = entry.getKey();
            String beanKey = null;

            int idx = memberKey.indexOf('.');
            if (idx == -1) {
                beanKey = "." + memberKey;
            } else {
                beanKey = memberKey.substring(0, idx);
                memberKey = memberKey.substring(idx + 1);
            }

            MultivaluedMap<String, String> value = parsedValues.get(beanKey);
            if (value == null) {
                value = new MetadataMap<String, String>();
                parsedValues.put(beanKey, value);
            }
            value.put(memberKey, entry.getValue());
        }

        if (parsedValues.size() > 0) {
            for (Map.Entry<String, MultivaluedMap<String, String>> entry : parsedValues.entrySet()) {
                String memberKey = entry.getKey();

                boolean isbean = !memberKey.startsWith(".");
                if (!isbean) {
                    memberKey = memberKey.substring(1);
                }

                Object setter = null;
                Object getter = null;
                for (Method m : paramType.getMethods()) {
                    if (m.getName().equalsIgnoreCase("set" + memberKey)
                        && m.getParameterTypes().length == 1) {
                        setter = m;
                    } else if (m.getName().equalsIgnoreCase("get" + memberKey)
                        || isBooleanType(m.getReturnType()) 
                           && m.getName().equalsIgnoreCase("is" + memberKey)) {
                        getter = m;
                    }
                    if (setter != null && getter != null) {
                        break;
                    }
                }
                if (setter == null) {
                    for (Field f : paramType.getFields()) {
                        if (f.getName().equalsIgnoreCase(memberKey)) {
                            setter = f;
                            getter = f;
                            break;
                        }
                    }
                }

                if (setter != null && getter != null) {
                    Class<?> type = null;
                    Type genericType = null;
                    Object paramValue = null;
                    if (setter instanceof Method) {
                        type = Method.class.cast(setter).getParameterTypes()[0];
                        genericType = Method.class.cast(setter).getGenericParameterTypes()[0];
                        paramValue = InjectionUtils.extractFromMethod(bean, (Method) getter);
                    } else {
                        type = Field.class.cast(setter).getType();
                        genericType = Field.class.cast(setter).getGenericType();
                        paramValue = InjectionUtils.extractFieldValue((Field) getter, bean);
                    }

                    List<MultivaluedMap<String, String>> processedValuesList =
                        processValues(type, genericType, entry.getValue(), isbean);

                    for (MultivaluedMap<String, String> processedValues : processedValuesList) {
                        if (InjectionUtils.isSupportedCollectionOrArray(type)) {
                            Object appendValue = InjectionUtils.injectIntoCollectionOrArray(type,
                                                            genericType, paramAnns, processedValues,
                                                            isbean, true,
                                                            pType, message);
                            paramValue = InjectionUtils.mergeCollectionsOrArrays(paramValue, appendValue,
                                                            genericType);
                        } else if (isSupportedMap(genericType)) {
                            Object appendValue = InjectionUtils.injectIntoMap(
                                type, genericType, paramAnns, processedValues, true, pType, message);
                            paramValue = InjectionUtils.mergeMap(paramValue, appendValue, genericType);

                        } else if (isbean) {
                            paramValue = InjectionUtils.handleBean(type, paramAnns, processedValues,
                                                            pType, message, decoded);
                        } else {
                            paramValue = InjectionUtils.handleParameter(
                                processedValues.values().iterator().next().get(0), 
                                decoded, type, paramAnns, pType, message);
                        }

                        if (paramValue != null) {
                            if (setter instanceof Method) {
                                InjectionUtils.injectThroughMethod(bean, (Method) setter, paramValue);
                            } else {
                                InjectionUtils.injectFieldValue((Field) setter, bean, paramValue);
                            }
                        }
                    }
                }
            }
        }
        
        return bean;
    }

    @SuppressWarnings("unchecked")
    private static Object mergeMap(Object first, Object second, Type genericType) {
        if (first == null) {
            return second;
        } else if (first instanceof Map) {
            Map.class.cast(first).putAll((Map<?, ?>) second);
            return first;
        }
        return null;
    }
    
    // CHECKSTYLE:OFF
    private static Object injectIntoMap(Class<?> rawType, Type genericType,
                                        Annotation[] paramAnns,
                                        MultivaluedMap<String, String> processedValues, 
                                        boolean decoded,
                                        ParameterType pathParam, Message message) {
    // CHECKSTYLE:ON
        ParameterizedType paramType = (ParameterizedType) genericType;
        ParameterizedType valueParamType = (ParameterizedType) InjectionUtils
                                   .getType(paramType.getActualTypeArguments(), 1);
        Class<?> valueType = (Class<?>) InjectionUtils.getType(valueParamType
                           .getActualTypeArguments(), 0);

        MultivaluedMap<String, Object> theValues = new MetadataMap<String, Object>();
           
        Set<Map.Entry<String, List<String>>> processedValuesEntrySet = processedValues.entrySet();
        for (Map.Entry<String, List<String>> processedValuesEntry : processedValuesEntrySet) {
            List<String> valuesList = processedValuesEntry.getValue();
            for (String value : valuesList) {
                Object o = InjectionUtils.handleParameter(value,
                                   decoded, valueType, paramAnns, pathParam, message);
                theValues.add(processedValuesEntry.getKey(), o);
            }
        }
        return theValues;
    }    

    
    private static boolean isSupportedMap(Type genericType) {
        Class<?> rawType = getRawType(genericType);
        if (Map.class.isAssignableFrom(rawType) && genericType instanceof ParameterizedType) {
            ParameterizedType paramType = (ParameterizedType) genericType;
            if (paramType.getActualTypeArguments().length == 2) {
                Class<?> firstType = getRawType(getType(paramType.getActualTypeArguments(), 0));
                Type secondType = getType(paramType.getActualTypeArguments(), 1);
                if (secondType instanceof ParameterizedType) {
                    Class<?> secondRawType = getRawType(secondType);
                    if (String.class == firstType && List.class.isAssignableFrom(secondRawType)) {
                        Class<?> listtype = getRawType(
                            getType(((ParameterizedType)secondType).getActualTypeArguments(), 0));
                        return InjectionUtils.isPrimitive(listtype);
                    }
                }
            } 
        }
        return false;
    }
    
    private static List<MultivaluedMap<String, String>> processValues(Class<?> type, Type genericType,
                                        MultivaluedMap<String, String> values,
                                        boolean isbean) {
        List<MultivaluedMap<String, String>> valuesList =
            new ArrayList<MultivaluedMap<String, String>>();

        if (isbean && InjectionUtils.isSupportedCollectionOrArray(type)) {
            Class<?> realType = InjectionUtils.getActualType(genericType);
            for (Map.Entry<String, List<String>> entry : values.entrySet()) {
                String memberKey = entry.getKey();
                Class<?> memberType = null;

                for (Method m : realType.getMethods()) {
                    if (m.getName().equalsIgnoreCase("set" + memberKey)
                        && m.getParameterTypes().length == 1) {
                        memberType = m.getParameterTypes()[0];
                        break;
                    }
                }
                if (memberType == null) {
                    for (Field f : realType.getFields()) {
                        if (f.getName().equalsIgnoreCase(memberKey)) {
                            memberType = f.getType();
                            break;
                        }
                    }
                }

                // Strip values tied to collection/array fields from beans that are within
                // collection/array themselves, the only way to support this would be to have
                // an indexing syntax for nested beans, perhaps like this:
                //    a(0).b=1&a(0).b=2&a(1).b=3&a(1).b=4
                // For now though we simply don't support this capability. To illustrate, the 'c'
                // param is dropped from this multivaluedmap example since it is a list:
                //    {c=[71, 81, 91, 72, 82, 92], a=[C1, C2], b=[790, 791]}
                if (memberType != null && InjectionUtils.isSupportedCollectionOrArray(memberType)) {
                    continue;
                }

                // Split multivaluedmap value list contents into separate multivaluedmap instances
                // whose list contents are only 1 level deep, for example: 
                //    {a=[C1, C2], b=[790, 791]}
                // becomes these 2 separate multivaluedmap instances:
                //    {a=[C1], b=[790]} and {a=[C2], b=[791]}
                int idx = 0;
                for (String value : entry.getValue()) {
                    MultivaluedMap<String, String> splitValues =
                        (idx < valuesList.size()) ? valuesList.get(idx) : null;
                    if (splitValues == null) {
                        splitValues = new MetadataMap<String, String>();
                        valuesList.add(splitValues);
                    }
                    splitValues.add(memberKey, value);
                    idx++;
                }
            }
        } else {
            valuesList.add(values);
        }

        return valuesList;
    }

    public static boolean isSupportedCollectionOrArray(Class<?> type) {
        return Collection.class.isAssignableFrom(type) || type.isArray();
    }

    @SuppressWarnings("unchecked")
    private static Object mergeCollectionsOrArrays(Object first, Object second, Type genericType) {
        if (first == null) {
            return second;
        } else if (first instanceof Collection) {
            Collection.class.cast(first).addAll((Collection<?>) second);
            return first;
        } else {
            int firstLen = Array.getLength(first);
            int secondLen = Array.getLength(second);
            Object mergedArray = Array.newInstance(InjectionUtils.getActualType(genericType),
                                                    firstLen + secondLen);
            System.arraycopy(first, 0, mergedArray, 0, firstLen);
            System.arraycopy(second, 0, mergedArray, firstLen, secondLen);
            return mergedArray;
        }
    }

    
    static Class<?> getCollectionType(Class<?> rawType) {
        Class<?> type = null;
        if (SortedSet.class.isAssignableFrom(rawType)) {
            type = TreeSet.class;
        } else if (Set.class.isAssignableFrom(rawType)) {
            type = HashSet.class;
        } else if (Collection.class.isAssignableFrom(rawType)) {
            type = ArrayList.class;
        }
        return type;
        
    }
    //CHECKSTYLE:OFF
    private static Object injectIntoCollectionOrArray(Class<?> rawType, 
                                                      Type genericType,
                                                      Annotation[] paramAnns, 
                                        MultivaluedMap<String, String> values,
                                        boolean isbean, boolean decoded,
                                        ParameterType pathParam, Message message) {
     //CHECKSTYLE:ON    
        Class<?> type = getCollectionType(rawType);

        Class<?> realType = rawType.isArray() ? rawType.getComponentType() 
                : InjectionUtils.getActualType(genericType);
        
        Object theValues = null;
        if (type != null) {
            try {
                theValues = type.newInstance();
            } catch (IllegalAccessException ex) {
                reportServerError("CLASS_ACCESS_FAILURE", type.getName());
            } catch (Exception ex) {
                reportServerError("CLASS_INSTANTIATION_FAILURE", type.getName());
            }
        } else {
            theValues = Array.newInstance(realType, isbean ? 1 : values.values().iterator().next().size());
        }
        if (isbean) {
            Object o = InjectionUtils.handleBean(realType, paramAnns, values, pathParam, message, decoded);
            addToCollectionValues(theValues, o, 0);
        } else {
            List<String> valuesList = values.values().iterator().next();
            valuesList = checkPathSegment(valuesList, realType, pathParam);
            for (int ind = 0; ind < valuesList.size(); ind++) {
                Object o = InjectionUtils.handleParameter(valuesList.get(ind), decoded, 
                                                          realType, paramAnns, pathParam, message);
                addToCollectionValues(theValues, o, ind);
            }
        }
        return theValues;
    }
    
    @SuppressWarnings("unchecked")
    private static void addToCollectionValues(Object theValues, Object o, int index) {
        if (o != null) {
            if (theValues instanceof Collection) {
                Collection.class.cast(theValues).add(o);
            } else {
                ((Object[]) theValues)[index] = o;
            }
        }
    }
    
    private static List<String> checkPathSegment(List<String> values, Class<?> type, 
                                                 ParameterType pathParam) {
        if (pathParam != ParameterType.PATH || !PathSegment.class.isAssignableFrom(type)) {
            return values;
        }
        List<String> newValues = new ArrayList<String>();
        for (String v : values) {
            String[] segments = v.split("/");
            for (String s : segments) {
                if (s.length() != 0) {
                    newValues.add(s);
                }
            }
            if (v.endsWith("/")) {
                newValues.add("");
            }
        }
        return newValues;
    }
    // 
    //CHECKSTYLE:OFF
    public static Object createParameterObject(List<String> paramValues,
                                               Class<?> paramType,
                                               Type genericType,
                                               Annotation[] paramAnns,
                                               String defaultValue,
                                               boolean decoded,
                                               ParameterType pathParam,
                                               Message message) {
    //CHECKSTYLE:ON    
        
        if (paramValues == null) {
            if (defaultValue != null) {
                paramValues = Collections.singletonList(defaultValue);
            } else {
                if (paramType.isPrimitive()) {
                    paramValues = Collections.singletonList(
                        boolean.class == paramType ? "false" : "0");
                } else if (InjectionUtils.isSupportedCollectionOrArray(paramType)) {
                    paramValues = Collections.emptyList();
                } else {
                    return null;
                }
            }
        }

        Object value = null;
        if (InjectionUtils.isSupportedCollectionOrArray(paramType)) {
            MultivaluedMap<String, String> paramValuesMap = new MetadataMap<String, String>();
            paramValuesMap.put("", paramValues);
            value = InjectionUtils.injectIntoCollectionOrArray(paramType, genericType, paramAnns, 
                                                paramValuesMap, false, decoded, pathParam, message);
        } else {
            String result = null;
            if (paramValues.size() > 0) {
                boolean isLast = pathParam == ParameterType.PATH ? true : false;
                result = isLast ? paramValues.get(paramValues.size() - 1)
                                : paramValues.get(0);
            }
            if (result != null) {
                value = InjectionUtils.handleParameter(result, decoded, paramType, 
                                                       paramAnns, pathParam, message);
            }
        }
        return value;
    }
    
    // TODO : investigate the possibility of using generic proxies only
    @SuppressWarnings("unchecked")
    public static <T> ThreadLocalProxy<T> createThreadLocalProxy(Class<T> type) {
        ThreadLocalProxy<?> proxy = null;
        if (UriInfo.class.isAssignableFrom(type)) {
            proxy = new ThreadLocalUriInfo();
        } else if (HttpHeaders.class.isAssignableFrom(type)) {
            proxy = new ThreadLocalHttpHeaders();
        } else if (ProtocolHeaders.class.isAssignableFrom(type)) {
            proxy = new ThreadLocalProtocolHeaders();
        } else if (SecurityContext.class.isAssignableFrom(type)) {
            proxy = new ThreadLocalSecurityContext();
        } else if (ContextResolver.class.isAssignableFrom(type)) {
            proxy = new ThreadLocalContextResolver<Object>();
        } else if (Request.class.isAssignableFrom(type)) {
            proxy = new ThreadLocalRequest();
        }  else if (Providers.class.isAssignableFrom(type)) {
            proxy = new ThreadLocalProviders();
        } else if (MessageContext.class.isAssignableFrom(type)) {
            proxy = new ThreadLocalMessageContext();
        }
        
        if (proxy == null && isServletApiContext(type.getName())) {
            proxy = createThreadLocalServletApiContext(type.getName());  
        }
        if (proxy == null) {
            return (ThreadLocalProxy<T>)Proxy.newProxyInstance(type.getClassLoader(),
                                   new Class[] {type, ThreadLocalProxy.class },
                                   new ThreadLocalInvocationHandler<T>());
        }
        
        return (ThreadLocalProxy<T>)proxy;
    }
    
    private static boolean isServletApiContext(String name) { 
        return name.startsWith("javax.servlet.");
    }
    
    private static ThreadLocalProxy<?> createThreadLocalServletApiContext(String name) {
        String proxyClassName = null;
        if (HTTP_SERVLET_REQUEST_CLASS_NAME.equals(name)) {
            proxyClassName = "org.apache.cxf.jaxrs.impl.tl.ThreadLocalHttpServletRequest";
        } else if (HTTP_SERVLET_RESPONSE_CLASS_NAME.equals(name)) {
            proxyClassName = "org.apache.cxf.jaxrs.impl.tl.ThreadLocalHttpServletResponse";
        } else if (SERVLET_CONTEXT_CLASS_NAME.equals(name)) {
            proxyClassName = "org.apache.cxf.jaxrs.impl.tl.ThreadLocalServletContext";
        } else if (SERVLET_CONFIG_CLASS_NAME.equals(name)) {
            proxyClassName = "org.apache.cxf.jaxrs.impl.tl.ThreadLocalServletConfig";
        }
        try {
            return (ThreadLocalProxy<?>)ClassLoaderUtils.loadClass(proxyClassName, InjectionUtils.class)
                .newInstance();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    
    public static void injectContextProxiesAndApplication(AbstractResourceInfo cri, 
                                                          Object instance,
                                                          Application app) {
        if (!cri.contextsAvailable() || !cri.isSingleton()) {
            return;
        }
        
        for (Map.Entry<Class<?>, Method> entry : cri.getContextMethods().entrySet()) {
            Method method = entry.getValue();
            Object value = method.getParameterTypes()[0] == Application.class 
                ? app : cri.getContextSetterProxy(method);
            InjectionUtils.injectThroughMethod(instance, method, value);
        }
        
        for (Field f : cri.getContextFields()) {
            Object value = f.getType() == Application.class ? app : cri.getContextFieldProxy(f);
            InjectionUtils.injectFieldValue(f, instance, value);
        }
    }
    
    public static void injectContextProxies(AbstractResourceInfo cri, Object instance) {
        injectContextProxiesAndApplication(cri, instance, null);
    }
    
    @SuppressWarnings("unchecked")
    public static void injectContextField(AbstractResourceInfo cri, 
                                          Field f, Object o, Object value) {
        if (!cri.isSingleton()) {
            InjectionUtils.injectFieldValue(f, o, value);
        } else {
            ThreadLocalProxy<Object> proxy = (ThreadLocalProxy<Object>)cri.getContextFieldProxy(f);
            if (proxy != null) {
                proxy.set(value);
            }
        }
    }
    
    public static void injectContexts(Object requestObject,
                                 AbstractResourceInfo resource,
                                 Message message) {
        if (resource.contextsAvailable()) {
            injectContextMethods(requestObject, resource, message);
            injectContextFields(requestObject, resource, message);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static void injectContextMethods(Object requestObject,
                                            AbstractResourceInfo cri,
                                            Message message) {
        
        for (Map.Entry<Class<?>, Method> entry : cri.getContextMethods().entrySet()) {
            Method method = entry.getValue();
            if (method.getParameterTypes()[0] == Application.class && cri.isSingleton()) {
                continue;
            }
            Object o = JAXRSUtils.createContextValue(message, 
                                              method.getGenericParameterTypes()[0],
                                              entry.getKey());
            
            if (o != null) {
                if (!cri.isSingleton()) {
                    InjectionUtils.injectThroughMethod(requestObject, method, o);
                } else {
                    ThreadLocalProxy<Object> proxy 
                        = (ThreadLocalProxy<Object>)cri.getContextSetterProxy(method);
                    if (proxy != null) {
                        proxy.set(o);
                    }
                }
                
            }
        }
    }
    
    public static void injectContextFields(Object o,
                                           AbstractResourceInfo cri,
                                           Message m) {
        
        for (Field f : cri.getContextFields()) {
            if (f.getType() == Application.class && cri.isSingleton()) {
                continue;
            }
            Object value = JAXRSUtils.createContextValue(m, f.getGenericType(), f.getType());
            InjectionUtils.injectContextField(cri, f, o, value);
        }
    }
    
    public static MultivaluedMap<String, Object> extractValuesFromBean(Object bean, String baseName) {
        MultivaluedMap<String, Object> values = new MetadataMap<String, Object>();
        fillInValuesFromBean(bean, baseName, values);
        return values;
    }
    
    private static boolean isBooleanType(Class<?> cls) {
        return boolean.class == cls || Boolean.class == cls;
    }
    
    public static void fillInValuesFromBean(Object bean, String baseName, 
                                            MultivaluedMap<String, Object> values) {
        for (Method m : bean.getClass().getMethods()) {
            String methodName = m.getName(); 
            boolean startsFromGet = methodName.startsWith("get");
            if ((startsFromGet 
                || isBooleanType(m.getReturnType()) && methodName.startsWith("is")) 
                && m.getParameterTypes().length == 0) {
                
                int minLen = startsFromGet ? 3 : 2; 
                if (methodName.length() <= minLen) {
                    continue;
                }
                
                String propertyName =  methodName.substring(minLen);
                if (propertyName.length() == 1) {
                    propertyName = propertyName.toLowerCase();
                } else {
                    propertyName = propertyName.substring(0, 1).toLowerCase()
                                   + propertyName.substring(1);
                }
                if (baseName.contains(propertyName) 
                    || "class".equals(propertyName)
                    || "declaringClass".equals(propertyName)) {
                    continue;
                }
                if (!"".equals(baseName)) {
                    propertyName = baseName + "." + propertyName;
                }
                
                Object value = extractFromMethod(bean, m);
                if (value == null) {
                    continue;
                }
                if (isPrimitive(value.getClass())) {
                    values.putSingle(propertyName, value);
                } else if (value.getClass().isEnum()) {
                    values.putSingle(propertyName, value.toString());
                } else if (isSupportedCollectionOrArray(value.getClass())) {
                    List<Object> theValues = null;
                    if (value.getClass().isArray()) {
                        theValues = Arrays.asList(value);
                    } else if (value instanceof Set) {
                        theValues = new ArrayList<Object>((Set<?>)value);
                    } else {
                        theValues = CastUtils.cast((List<?>)value);
                    }
                    values.put(propertyName, theValues);
                } else {
                    fillInValuesFromBean(value, propertyName, values);
                }
            }
        }
    }
    
    public static Map<Parameter, Class<?>> getParametersFromBeanClass(Class<?> beanClass, 
                                                                      ParameterType type,
                                                                      boolean checkIgnorable) {
        Map<Parameter, Class<?>> params = new LinkedHashMap<Parameter, Class<?>>();
        for (Method m : beanClass.getMethods()) {
            String methodName = m.getName(); 
            boolean startsFromGet = methodName.startsWith("get");
            if ((startsFromGet 
                || isBooleanType(m.getReturnType()) && methodName.startsWith("is")) 
                && m.getParameterTypes().length == 0) {
                
                int minLen = startsFromGet ? 3 : 2; 
                if (methodName.length() <= minLen) {
                    continue;
                }
                String propertyName = methodName.substring(minLen).toLowerCase();
                if (m.getReturnType() == Class.class
                    || checkIgnorable && canPropertyBeIgnored(m, propertyName)) {
                    continue;
                }
                params.put(new Parameter(type, propertyName), m.getReturnType());
            }
        }
        return params;
    }
    
    private static boolean canPropertyBeIgnored(Method m, String propertyName) {
        for (Annotation ann : m.getAnnotations()) {
            String annType = ann.annotationType().getName();
            if ("org.apache.cxf.aegis.type.java5.IgnoreProperty".equals(annType)
                || "javax.xml.bind.annotation.XmlTransient".equals(annType)) {
                return true;
            }
        }
        return false;
    }
    
    public static boolean isPrimitive(Class<?> type) {
        return type.isPrimitive() 
            || Number.class.isAssignableFrom(type)
            || Boolean.class.isAssignableFrom(type)
            || String.class == type;
    }
    
    public static String decodeValue(String value, boolean decode, ParameterType param) {
        if (!decode) {
            return value;
        }
        if (param == ParameterType.PATH || param == ParameterType.MATRIX) {
            return HttpUtils.pathDecode(value);
        } else {
            return HttpUtils.urlDecode(value);
        }
    }
    
    public static void invokeLifeCycleMethod(Object instance, Method method) {
        if (method != null) {
            method = InjectionUtils.checkProxy(method, instance);
            try {
                method.invoke(instance, new Object[]{});
            } catch (InvocationTargetException ex) {
                String msg = "Method " + method.getName() + " can not be invoked"
                    + " due to InvocationTargetException";
                throw new WebApplicationException(Response.serverError().entity(msg).build());
            } catch (IllegalAccessException ex) {
                String msg = "Method " + method.getName() + " can not be invoked"
                    + " due to IllegalAccessException";
                throw new InternalServerErrorException(Response.serverError().entity(msg).build());
            } 
        }
    }
    
    public static <T> Object convertStringToPrimitive(String value, Class<?> cls) {
        if (String.class == cls) {
            return value;
        }
        if (cls.isPrimitive()) {
            return PrimitiveUtils.read(value, cls);
        } else if (cls.isEnum()) {
            try {
                Method m  = cls.getMethod("valueOf", new Class[]{String.class});
                return m.invoke(null, value.toUpperCase());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } else {
            try {
                Constructor<?> c = cls.getConstructor(new Class<?>[]{String.class});
                return c.newInstance(new Object[]{value});
            } catch (Throwable ex) {
                // try valueOf
            }
            try {
                Method m = cls.getMethod("valueOf", new Class[]{String.class});
                return cls.cast(m.invoke(null, value));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
