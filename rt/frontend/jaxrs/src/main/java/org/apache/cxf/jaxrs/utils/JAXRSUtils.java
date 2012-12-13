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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.Consumes;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.NotSupportedException;
import javax.ws.rs.Produces;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;
import javax.ws.rs.ext.ReaderInterceptor;
import javax.ws.rs.ext.ReaderInterceptorContext;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import javax.xml.namespace.QName;

import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.PackageUtils;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.helpers.XMLUtils;
import org.apache.cxf.jaxrs.ext.ContextProvider;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.ext.MessageContextImpl;
import org.apache.cxf.jaxrs.ext.ProtocolHeaders;
import org.apache.cxf.jaxrs.ext.ProtocolHeadersImpl;
import org.apache.cxf.jaxrs.ext.multipart.MultipartBody;
import org.apache.cxf.jaxrs.impl.AsyncResponseImpl;
import org.apache.cxf.jaxrs.impl.ContainerRequestContextImpl;
import org.apache.cxf.jaxrs.impl.ContainerResponseContextImpl;
import org.apache.cxf.jaxrs.impl.HttpHeadersImpl;
import org.apache.cxf.jaxrs.impl.HttpServletResponseFilter;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.impl.PathSegmentImpl;
import org.apache.cxf.jaxrs.impl.ProvidersImpl;
import org.apache.cxf.jaxrs.impl.ReaderInterceptorContextImpl;
import org.apache.cxf.jaxrs.impl.ReaderInterceptorMBR;
import org.apache.cxf.jaxrs.impl.RequestImpl;
import org.apache.cxf.jaxrs.impl.ResourceInfoImpl;
import org.apache.cxf.jaxrs.impl.SecurityContextImpl;
import org.apache.cxf.jaxrs.impl.UriInfoImpl;
import org.apache.cxf.jaxrs.impl.WriterInterceptorContextImpl;
import org.apache.cxf.jaxrs.impl.WriterInterceptorMBW;
import org.apache.cxf.jaxrs.model.BeanParamInfo;
import org.apache.cxf.jaxrs.model.BeanResourceInfo;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.ClassResourceInfoComparator;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfoComparator;
import org.apache.cxf.jaxrs.model.Parameter;
import org.apache.cxf.jaxrs.model.ParameterType;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.model.URITemplate;
import org.apache.cxf.jaxrs.provider.AbstractConfigurableProvider;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.utils.multipart.AttachmentUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

public final class JAXRSUtils {

    public static final MediaType ALL_TYPES = new MediaType();
    public static final String ROOT_RESOURCE_CLASS = "root.resource.class";
    public static final String IGNORE_MESSAGE_WRITERS = "ignore.message.writers";
    public static final String ROOT_INSTANCE = "service.root.instance";
    public static final String ROOT_PROVIDER = "service.root.provider";
    public static final String DOC_LOCATION = "wadl.location";
    
    private static final Logger LOG = LogUtils.getL7dLogger(JAXRSUtils.class);
    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(JAXRSUtils.class);
    private static final String PROPAGATE_EXCEPTION = "org.apache.cxf.propagate.exception";
    private static final String REPORT_FAULT_MESSAGE_PROPERTY = "org.apache.cxf.jaxrs.report-fault-message";
    
    private static final Map<Integer, Class<?>> EXCEPTIONS_MAP;
    static {
        EXCEPTIONS_MAP = new HashMap<Integer, Class<?>>();
        EXCEPTIONS_MAP.put(400, BadRequestException.class);
        EXCEPTIONS_MAP.put(401, NotAuthorizedException.class);
        EXCEPTIONS_MAP.put(404, NotFoundException.class);
        EXCEPTIONS_MAP.put(405, NotAllowedException.class);
        EXCEPTIONS_MAP.put(406, NotAcceptableException.class);
        EXCEPTIONS_MAP.put(415, NotSupportedException.class);
        EXCEPTIONS_MAP.put(500, InternalServerErrorException.class);
        EXCEPTIONS_MAP.put(503, ServiceUnavailableException.class);
    }
    
    private JAXRSUtils() {        
    }
    
    public static List<PathSegment> getPathSegments(String thePath, boolean decode) {
        return getPathSegments(thePath, decode, true);
    }
    
    public static List<PathSegment> getPathSegments(String thePath, boolean decode, 
                                                    boolean ignoreLastSlash) {
        String[] segments = thePath.split("/");
        List<PathSegment> theList = new ArrayList<PathSegment>();
        for (String path : segments) {
            if (!StringUtils.isEmpty(path)) {
                theList.add(new PathSegmentImpl(path, decode));
            }
        }
        int len = thePath.length();
        if (len > 0 && thePath.charAt(len - 1) == '/') {
            String value = ignoreLastSlash ? "" : "/";
            theList.add(new PathSegmentImpl(value, false));
        }
        return theList;
    }

    @SuppressWarnings("unchecked")
    private static String[] getUserMediaTypes(Object provider, String methodName) {
        String[] values = null;
        if (AbstractConfigurableProvider.class.isAssignableFrom(provider.getClass())) {
            try {
                Method m = provider.getClass().getMethod(methodName, new Class[]{});
                List<String> types = (List<String>)m.invoke(provider, new Object[]{});
                if (types != null) {
                    values = types.size() > 0 ? types.toArray(new String[types.size()])
                                               : new String[]{"*/*"};
                }
            } catch (Exception ex) {
                // ignore
            }
        }
        return values;
    }
    
    public static List<MediaType> getProviderConsumeTypes(MessageBodyReader<?> provider) {
        String[] values = getUserMediaTypes(provider, "getConsumeMediaTypes");
        
        if (values == null) {
            return getConsumeTypes(provider.getClass().getAnnotation(Consumes.class));
        } else {
            return JAXRSUtils.getMediaTypes(values);
        }
    }
    
    public static List<MediaType> getProviderProduceTypes(MessageBodyWriter<?> provider) {
        String[] values = getUserMediaTypes(provider, "getProduceMediaTypes");
        if (values == null) {
            return getProduceTypes(provider.getClass().getAnnotation(Produces.class));
        } else {
            return JAXRSUtils.getMediaTypes(values);
        }
    }
    
    public static List<MediaType> getMediaTypes(String[] values) {
        List<MediaType> supportedMimeTypes = new ArrayList<MediaType>(values.length);
        for (int i = 0; i < values.length; i++) {
            supportedMimeTypes.add(MediaType.valueOf(values[i]));    
        }
        return supportedMimeTypes;
    }
    
    public static void injectParameters(OperationResourceInfo ori,
                                        Object requestObject,
                                        Message message) {
        injectParameters(ori, ori.getClassResourceInfo(), requestObject, message);    
    }
    
    @SuppressWarnings("unchecked")
    public static void injectParameters(OperationResourceInfo ori,
                                        BeanResourceInfo bri,
                                        Object requestObject,
                                        Message message) {
                
        if (bri.isSingleton() 
            && (!bri.getParameterMethods().isEmpty() || !bri.getParameterFields().isEmpty())) {
            LOG.fine("Injecting request parameters into singleton resource is not thread-safe");
        }
        // Param methods
        MultivaluedMap<String, String> values = 
            (MultivaluedMap<String, String>)message.get(URITemplate.TEMPLATE_PARAMETERS);
        for (Method m : bri.getParameterMethods()) {
            Parameter p = ResourceUtils.getParameter(0, m.getAnnotations(), 
                                                     m.getParameterTypes()[0]);
            Object o;
            
            if (p.getType() == ParameterType.BEAN && bri instanceof ClassResourceInfo) {
                o = createBeanParamValue(message, m.getParameterTypes()[0], ori);    
            } else {
                o = createHttpParameterValue(p, 
                                                m.getParameterTypes()[0],
                                                m.getGenericParameterTypes()[0],
                                                m.getParameterAnnotations()[0],
                                                message,
                                                values,
                                                ori);
            }
            InjectionUtils.injectThroughMethod(requestObject, m, o);
        }
        // Param fields
        for (Field f : bri.getParameterFields()) {
            Parameter p = ResourceUtils.getParameter(0, f.getAnnotations(), 
                                                     f.getType());
            Object o = null;
            
            if (p.getType() == ParameterType.BEAN && bri instanceof ClassResourceInfo) {
                o = createBeanParamValue(message, f.getType(), ori);    
            } else {
                o = createHttpParameterValue(p, 
                                                f.getType(),
                                                f.getGenericType(),
                                                f.getAnnotations(),
                                                message,
                                                values,
                                                ori);
            }
            InjectionUtils.injectFieldValue(f, requestObject, o);
        }
        
    }
    
    public static ClassResourceInfo selectResourceClass(List<ClassResourceInfo> resources,
                                                 String path, 
                                                 MultivaluedMap<String, String> values,
                                                 Message message) {
        boolean isFineLevelLoggable = LOG.isLoggable(Level.FINE); 
        if (isFineLevelLoggable) {
            LOG.fine(new org.apache.cxf.common.i18n.Message("START_CRI_MATCH", 
                                                        BUNDLE, 
                                                        path).toString());
        }
        if (resources.size() == 1) { 
            return resources.get(0).getURITemplate().match(path, values)
                   ? resources.get(0) : null;
        }
        
        SortedMap<ClassResourceInfo, MultivaluedMap<String, String>> candidateList = 
            new TreeMap<ClassResourceInfo, MultivaluedMap<String, String>>(
                new ClassResourceInfoComparator(message));
        
        for (ClassResourceInfo cri : resources) {
            MultivaluedMap<String, String> map = new MetadataMap<String, String>();
            if (cri.getURITemplate().match(path, map)) {
                candidateList.put(cri, map);
                if (isFineLevelLoggable) {
                    LOG.fine(new org.apache.cxf.common.i18n.Message("CRI_SELECTED_POSSIBLY", 
                                                                BUNDLE, 
                                                                cri.getServiceClass().getName(),
                                                                path, 
                                                                cri.getURITemplate().getValue()).toString());
                }
            } else if (isFineLevelLoggable) {
                LOG.fine(new org.apache.cxf.common.i18n.Message("CRI_NO_MATCH", 
                                                                BUNDLE, 
                                                                path,
                                                                cri.getServiceClass().getName()).toString());
            }
        }
        
        if (!candidateList.isEmpty()) {
            Map.Entry<ClassResourceInfo, MultivaluedMap<String, String>> firstEntry = 
                candidateList.entrySet().iterator().next();
            values.putAll(firstEntry.getValue());
            ClassResourceInfo cri = firstEntry.getKey();
            if (isFineLevelLoggable) {
                LOG.fine(new org.apache.cxf.common.i18n.Message("CRI_SELECTED", 
                                                         BUNDLE, 
                                                         cri.getServiceClass().getName(),
                                                         path, cri.getURITemplate().getValue()).toString());
            }
            return cri;
        }
        
        return null;
    }

    public static OperationResourceInfo findTargetMethod(ClassResourceInfo resource,
                                                         Message message,
                                                         String httpMethod, 
                                                         MultivaluedMap<String, String> values, 
                                                         String requestContentType, 
                                                         List<MediaType> acceptContentTypes,
                                                         boolean logNow) {
        boolean isFineLevelLoggable = LOG.isLoggable(Level.FINE); 
        if (isFineLevelLoggable) {
            org.apache.cxf.common.i18n.Message msg = 
                new org.apache.cxf.common.i18n.Message("START_OPER_MATCH", 
                                                       BUNDLE,
                                                       resource.getServiceClass().getName());
            LOG.fine(msg.toString());
            
        }
        String path = values.getFirst(URITemplate.FINAL_MATCH_GROUP);
        if (path == null) {
            path = "/";
        }
        
        SortedMap<OperationResourceInfo, MultivaluedMap<String, String>> candidateList = 
            new TreeMap<OperationResourceInfo, MultivaluedMap<String, String>>(
                new OperationResourceInfoComparator(message, httpMethod));

        MediaType requestType;
        try {
            requestType = requestContentType == null
                                ? ALL_TYPES : MediaType.valueOf(requestContentType);
        } catch (IllegalArgumentException ex) {
            throw new NotSupportedException(ex);
        }

        int pathMatched = 0;
        int methodMatched = 0;
        int consumeMatched = 0;
        int produceMatched = 0;
        
        boolean subresourcesOnly = true;
        for (MediaType acceptType : acceptContentTypes) {
            for (OperationResourceInfo ori : resource.getMethodDispatcher().getOperationResourceInfos()) {
                URITemplate uriTemplate = ori.getURITemplate();
                MultivaluedMap<String, String> map = new MetadataMap<String, String>(values);
                if (uriTemplate != null && uriTemplate.match(path, map)) {
                    boolean added = false;
                    if (ori.isSubResourceLocator()) {
                        candidateList.put(ori, map);
                        added = true;
                    } else {
                        String finalGroup = map.getFirst(URITemplate.FINAL_MATCH_GROUP);
                        if (finalGroup == null || StringUtils.isEmpty(finalGroup)
                            || finalGroup.equals("/")) {
                            pathMatched++;
                            boolean mMatched = matchHttpMethod(ori.getHttpMethod(), httpMethod);
                            boolean cMatched = matchConsumeTypes(requestType, ori);
                            boolean pMatched = matchProduceTypes(acceptType, ori);
                            if (mMatched && cMatched && pMatched) {
                                subresourcesOnly = false;
                                candidateList.put(ori, map);
                                added = true;
                            } else {
                                methodMatched = mMatched ? methodMatched + 1 : methodMatched;
                                produceMatched = pMatched ? produceMatched + 1 : produceMatched;
                                consumeMatched = cMatched ? consumeMatched + 1 : consumeMatched;
                                logNoMatchMessage(ori, path, httpMethod, requestType, acceptContentTypes);
                            }
                        } else {
                            logNoMatchMessage(ori, path, httpMethod, requestType, acceptContentTypes);
                        }
                    }
                    if (added && isFineLevelLoggable) {
                        LOG.fine(new org.apache.cxf.common.i18n.Message("OPER_SELECTED_POSSIBLY", 
                                  BUNDLE, 
                                  ori.getMethodToInvoke().getName()).toString());
                    }
                } else {
                    logNoMatchMessage(ori, path, httpMethod, requestType, acceptContentTypes);
                }
            }
            if (!candidateList.isEmpty() && !subresourcesOnly) {
                break;
            }
        }
        if (!candidateList.isEmpty()) {
            Map.Entry<OperationResourceInfo, MultivaluedMap<String, String>> firstEntry = 
                candidateList.entrySet().iterator().next();
            values.clear();
            values.putAll(firstEntry.getValue());
            OperationResourceInfo ori = firstEntry.getKey();
            if (headMethodPossible(ori.getHttpMethod(), httpMethod)) {
                LOG.info(new org.apache.cxf.common.i18n.Message("GET_INSTEAD_OF_HEAD", 
                         BUNDLE, resource.getServiceClass().getName(), 
                         ori.getMethodToInvoke().getName()).toString());
            }
            if (isFineLevelLoggable) {
                LOG.fine(new org.apache.cxf.common.i18n.Message("OPER_SELECTED", 
                               BUNDLE, ori.getMethodToInvoke().getName(), 
                               resource.getServiceClass().getName()).toString());
            }
            return ori;
        }
        
        int status;
        
        // criteria matched the least number of times will determine the error code;
        // priority : path, method, consumes, produces;
        if (pathMatched == 0) {
            status = 404;
        } else if (methodMatched == 0) {
            status = 405;
        } else if (consumeMatched <= produceMatched) {
            status = 415;
        } else {
            status = 406;
        }
        
        String name = resource.isRoot() ? "NO_OP_EXC" : "NO_SUBRESOURCE_METHOD_FOUND";
        org.apache.cxf.common.i18n.Message errorMsg = 
            new org.apache.cxf.common.i18n.Message(name, 
                                                   BUNDLE,
                                                   message.get(Message.REQUEST_URI),
                                                   path,
                                                   httpMethod,
                                                   requestType.toString(),
                                                   convertTypesToString(acceptContentTypes));
        if (!"OPTIONS".equalsIgnoreCase(httpMethod) && logNow) {
            LOG.warning(errorMsg.toString());
        }
        Response response = 
            createResponse(resource, message, errorMsg.toString(), status, methodMatched == 0);
        throw new ClientErrorException(response);
        
    }    
    
    public static boolean noResourceMethodForOptions(Response exResponse, String httpMethod) {
        return exResponse != null && exResponse.getStatus() == 405 
            && "OPTIONS".equalsIgnoreCase(httpMethod);
    }
    
    private static void logNoMatchMessage(OperationResourceInfo ori, 
        String path, String httpMethod, MediaType requestType, List<MediaType> acceptContentTypes) {
        if (!LOG.isLoggable(Level.FINE)) {
            return;
        }
        org.apache.cxf.common.i18n.Message errorMsg = 
            new org.apache.cxf.common.i18n.Message("OPER_NO_MATCH", 
                                                   BUNDLE,
                                                   ori.getMethodToInvoke().getName(),
                                                   path,
                                                   ori.getURITemplate().getValue(),
                                                   httpMethod,
                                                   ori.getHttpMethod(),
                                                   requestType.toString(),
                                                   convertTypesToString(ori.getConsumeTypes()),
                                                   convertTypesToString(acceptContentTypes),
                                                   convertTypesToString(ori.getProduceTypes()));
        LOG.fine(errorMsg.toString());
    }

    public static Response createResponse(ClassResourceInfo cri, Message msg,
                                          String responseMessage, int status, boolean addAllow) {
        ResponseBuilder rb = Response.status(status);
        if (addAllow) {
            Set<String> allowedMethods = cri.getAllowedMethods();
            for (String m : allowedMethods) {
                rb.header("Allow", m);
            }
            // "OPTIONS" are supported all the time really
            if (!allowedMethods.contains("OPTIONS")) {
                rb.header("Allow", "OPTIONS");
            }
            if (!allowedMethods.contains("HEAD") && allowedMethods.contains("GET")) {
                rb.header("Allow", "HEAD");
            }
        }
        if (msg != null && MessageUtils.isTrue(msg.getContextualProperty(REPORT_FAULT_MESSAGE_PROPERTY))) {
            rb.type(MediaType.TEXT_PLAIN_TYPE).entity(responseMessage);
        }
        return rb.build();
    }
    
    private static boolean matchHttpMethod(String expectedMethod, String httpMethod) {
        if (expectedMethod.equalsIgnoreCase(httpMethod) 
            || headMethodPossible(expectedMethod, httpMethod)) {
            return true;
        }
        return false;
    }
    
    public static boolean headMethodPossible(String expectedMethod, String httpMethod) {
        return "HEAD".equalsIgnoreCase(httpMethod) && "GET".equals(expectedMethod);        
    }
    
    private static String convertTypesToString(List<MediaType> types) {
        StringBuilder sb = new StringBuilder();
        for (MediaType type : types) {
            sb.append(type.toString()).append(',');
        }
        return sb.toString();
    }
    
    public static List<MediaType> getConsumeTypes(Consumes cm) {
        return cm == null ? Collections.singletonList(ALL_TYPES)
                          : getMediaTypes(cm.value());
    }
    
    public static List<MediaType> getProduceTypes(Produces pm) {
        return pm == null ? Collections.singletonList(ALL_TYPES)
                          : getMediaTypes(pm.value());
    }
    
    public static int compareSortedMediaTypes(List<MediaType> mts1, List<MediaType> mts2) {
        int size1 = mts1.size();
        int size2 = mts2.size();
        for (int i = 0; i < size1 && i < size2; i++) {
            int result = compareMediaTypes(mts1.get(i), mts2.get(i));
            if (result != 0) {
                return result;
            }
        }
        return size1 == size2 ? 0 : size1 < size2 ? -1 : 1;
    }
    
    public static int compareMediaTypes(MediaType mt1, MediaType mt2) {
        
        if (mt1.isWildcardType() && !mt2.isWildcardType()) {
            return 1;
        }
        if (!mt1.isWildcardType() && mt2.isWildcardType()) {
            return -1;
        }
         
        
        if (mt1.isWildcardSubtype() && !mt2.isWildcardSubtype()) {
            return 1;
        }
        if (!mt1.isWildcardSubtype() && mt2.isWildcardSubtype()) {
            return -1;
        }       
        
        return compareMediaTypesQualityFactors(mt1, mt2);
    }
    
    public static int compareMediaTypesQualityFactors(MediaType mt1, MediaType mt2) {
        float q1 = getMediaTypeQualityFactor(mt1.getParameters().get("q"));
        float q2 = getMediaTypeQualityFactor(mt2.getParameters().get("q"));
        return Float.compare(q1, q2) * -1;
    }
    

    public static float getMediaTypeQualityFactor(String q) {
        if (q == null) {
            return 1;
        }
        if (q.charAt(0) == '.') {
            q = '0' + q;
        }
        try {
            return Float.parseFloat(q);
        } catch (NumberFormatException ex) {
            // default value will do
        }
        return 1;
    }
    
    //Message contains following information: PATH, HTTP_REQUEST_METHOD, CONTENT_TYPE, InputStream.
    public static List<Object> processParameters(OperationResourceInfo ori, 
                                                 MultivaluedMap<String, String> values, 
                                                 Message message)
        throws IOException, WebApplicationException {
        
        
        Method method = ori.getMethodToInvoke();
        Class<?>[] parameterTypes = method.getParameterTypes();
        Parameter[] paramsInfo = ori.getParameters().toArray(new Parameter[ori.getParameters().size()]);  
        Method annotatedMethod = ori.getAnnotatedMethod();
        Type[] genericParameterTypes = annotatedMethod == null ? method.getGenericParameterTypes() 
                                      : annotatedMethod.getGenericParameterTypes();
        Annotation[][] anns = annotatedMethod == null ? null : annotatedMethod.getParameterAnnotations();
        List<Object> params = new ArrayList<Object>(parameterTypes.length);

        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> param = parameterTypes[i]; 
            Type genericParam = genericParameterTypes[i];
            if (genericParam instanceof TypeVariable) {
                genericParam = InjectionUtils.getSuperType(ori.getClassResourceInfo().getServiceClass(), 
                                                           (TypeVariable<?>)genericParam);
            }
            if (param == Object.class) {
                param = (Class<?>)genericParam; 
            } else if (genericParam == Object.class) {
                genericParam = param;
            }
            
            
            Object paramValue = processParameter(param, 
                                                 genericParam,
                                                 anns == null ? new Annotation[0] : anns[i],
                                                 paramsInfo[i], 
                                                 values, 
                                                 message,
                                                 ori);
            params.add(paramValue);
        }

        return params;
    }

    private static Object processParameter(Class<?> parameterClass, 
                                           Type parameterType,
                                           Annotation[] parameterAnns,
                                           Parameter parameter, 
                                           MultivaluedMap<String, String> values,
                                           Message message,
                                           OperationResourceInfo ori) 
        throws IOException, WebApplicationException {
        InputStream is = message.getContent(InputStream.class);

        if (parameter.getType() == ParameterType.REQUEST_BODY) {
            
            if (parameterClass == AsyncResponse.class) {
                return new AsyncResponseImpl(message);
            }
            
            String contentType = (String)message.get(Message.CONTENT_TYPE);

            if (contentType == null) {
                org.apache.cxf.common.i18n.Message errorMsg = 
                    new org.apache.cxf.common.i18n.Message("NO_CONTENT_TYPE_SPECIFIED", 
                                                           BUNDLE, 
                                                           ori.getHttpMethod());
                LOG.fine(errorMsg.toString());
                contentType = MediaType.WILDCARD;
            }

            return readFromMessageBody(parameterClass,
                                       parameterType,
                                       parameterAnns,
                                       is, 
                                       MediaType.valueOf(contentType),
                                       ori.getConsumeTypes(),
                                       message);
        } else if (parameter.getType() == ParameterType.CONTEXT) {
            return createContextValue(message, parameterType, parameterClass);
        } else if (parameter.getType() == ParameterType.BEAN) {
            return createBeanParamValue(message, parameterClass, ori);
        } else {
            
            return createHttpParameterValue(parameter,
                                            parameterClass,
                                            parameterType,
                                            parameterAnns,
                                            message,
                                            values,
                                            ori);
        }
    }
    
    public static Object createHttpParameterValue(Parameter parameter, 
                                            Class<?> parameterClass, 
                                            Type genericParam,
                                            Annotation[] paramAnns,
                                            Message message,
                                            MultivaluedMap<String, String> values,
                                            OperationResourceInfo ori) {
       
        boolean isEncoded = parameter.isEncoded() || ori != null && ori.isEncodedEnabled();
        String defaultValue = parameter.getDefaultValue();
        if (defaultValue == null && ori != null) {
            defaultValue = ori.getDefaultParameterValue();
        }
        
        Object result = null;
        
        if (parameter.getType() == ParameterType.PATH) {
            result = readFromUriParam(message, parameter.getName(), parameterClass, genericParam,
                                      paramAnns, values, defaultValue, !isEncoded);
        } 
        
        if (parameter.getType() == ParameterType.QUERY) {
            result = readQueryString(parameter.getName(), parameterClass, genericParam, 
                                     paramAnns, message, defaultValue, !isEncoded);
        }
        
        if (parameter.getType() == ParameterType.MATRIX) {
            result = processMatrixParam(message, parameter.getName(), parameterClass, genericParam,
                                        paramAnns, defaultValue, !isEncoded);
        }
        
        if (parameter.getType() == ParameterType.FORM) {
            result = processFormParam(message, parameter.getName(), parameterClass, genericParam, 
                                      paramAnns, defaultValue, !isEncoded);
        }
        
        if (parameter.getType() == ParameterType.COOKIE) {
            result = processCookieParam(message, parameter.getName(), parameterClass, genericParam,
                                        paramAnns, defaultValue);
        } 
        
        if (parameter.getType() == ParameterType.HEADER) {
            result = processHeaderParam(message, parameter.getName(), parameterClass, genericParam,
                                        paramAnns, defaultValue);
        } 

        return result;
    }
    
    private static Object processMatrixParam(Message m, String key, 
                                             Class<?> pClass, Type genericType,
                                             Annotation[] paramAnns,
                                             String defaultValue,
                                             boolean decode) {
        List<PathSegment> segments = JAXRSUtils.getPathSegments(
                                      (String)m.get(Message.REQUEST_URI), decode);
        if (segments.size() > 0) {
            MultivaluedMap<String, String> params = new MetadataMap<String, String>(); 
            for (PathSegment ps : segments) {
                MultivaluedMap<String, String> matrix = ps.getMatrixParameters();
                for (Map.Entry<String, List<String>> entry : matrix.entrySet()) {
                    for (String value : entry.getValue()) {                    
                        params.add(entry.getKey(), value);
                    }
                }
            }
            
            if ("".equals(key)) {
                return InjectionUtils.handleBean(pClass, paramAnns, params, ParameterType.MATRIX, m, false);
            } else {
                List<String> values = params.get(key);
                return InjectionUtils.createParameterObject(values, 
                                                            pClass, 
                                                            genericType,
                                                            paramAnns, 
                                                            defaultValue,
                                                            false,
                                                            ParameterType.MATRIX,
                                                            m);
            }
        }
        
        return null;
    }
    
    private static Object processFormParam(Message m, String key, 
                                           Class<?> pClass, Type genericType,
                                           Annotation[] paramAnns,
                                           String defaultValue,
                                           boolean decode) {
        
        MessageContext mc = new MessageContextImpl(m);
        MediaType mt = mc.getHttpHeaders().getMediaType();
        
        @SuppressWarnings("unchecked")
        MultivaluedMap<String, String> params = 
            (MultivaluedMap<String, String>)m.get(FormUtils.FORM_PARAM_MAP); 
        
        if (params == null) {
            params = new MetadataMap<String, String>();
            m.put(FormUtils.FORM_PARAM_MAP, params);
        
            if (mt == null || mt.isCompatible(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) {
                String enc = HttpUtils.getEncoding(mt, "UTF-8");
                String body = FormUtils.readBody(m.getContent(InputStream.class), enc);
                HttpServletRequest request = (HttpServletRequest)m.get(AbstractHTTPDestination.HTTP_REQUEST);
                FormUtils.populateMapFromString(params, m, body, enc, decode, request);
            } else {
                if ("multipart".equalsIgnoreCase(mt.getType()) 
                    && MediaType.MULTIPART_FORM_DATA_TYPE.isCompatible(mt)) {
                    MultipartBody body = AttachmentUtils.getMultipartBody(mc);
                    FormUtils.populateMapFromMultipart(params, body, m, decode);
                } else {
                    org.apache.cxf.common.i18n.Message errorMsg = 
                        new org.apache.cxf.common.i18n.Message("WRONG_FORM_MEDIA_TYPE", 
                                                               BUNDLE, 
                                                               mt.toString());
                    LOG.warning(errorMsg.toString());
                    throw new NotSupportedException();
                }
            }
        }
        
        if ("".equals(key)) {
            return InjectionUtils.handleBean(pClass, paramAnns, params, ParameterType.FORM, m, false);
        } else {
            List<String> results = params.get(key);
    
            return InjectionUtils.createParameterObject(results, 
                                                        pClass, 
                                                        genericType,
                                                        paramAnns,
                                                        defaultValue,
                                                        false,
                                                        ParameterType.FORM,
                                                        m);
             
        }
    }
    
    
    public static MultivaluedMap<String, String> getMatrixParams(String path, boolean decode) {
        int index = path.indexOf(';');
        return index == -1 ? new MetadataMap<String, String>()
                           : JAXRSUtils.getStructuredParams(path.substring(index + 1), ";", decode, false);
    }
    
    private static Object processHeaderParam(Message m, 
                                             String header, 
                                             Class<?> pClass,
                                             Type genericType, 
                                             Annotation[] paramAnns,
                                             String defaultValue) {
        
        List<String> values = new HttpHeadersImpl(m).getRequestHeader(header);
        if (values != null && values.isEmpty()) {
            values = null;
        }
        return InjectionUtils.createParameterObject(values, 
                                                    pClass, 
                                                    genericType,
                                                    paramAnns,
                                                    defaultValue,
                                                    false,
                                                    ParameterType.HEADER,
                                                    m);
             
        
    }

    private static Object processCookieParam(Message m, String cookieName, 
                              Class<?> pClass, Type genericType, 
                              Annotation[] paramAnns, String defaultValue) {
        Cookie c = new HttpHeadersImpl(m).getCookies().get(cookieName);
        
        if (c == null && defaultValue != null) {
            c = Cookie.valueOf(cookieName + '=' + defaultValue);
        }
        if (c == null) {
            return null;
        }
        
        if (pClass.isAssignableFrom(Cookie.class)) {
            return c;
        }
        
        return InjectionUtils.handleParameter(c.getValue(), false, pClass, paramAnns, 
                                              ParameterType.COOKIE, m);
    }
    
    public static Object createBeanParamValue(Message m, Class<?> clazz, OperationResourceInfo ori) {
        BeanParamInfo bmi = ProviderFactory.getInstance(m).getBeanParamInfo(clazz);
        if (bmi == null) {
            // we could've started introspecting now but the fact no bean info 
            // is available indicates that the one created at start up has been 
            // lost and hence it is 500
            LOG.warning("Bean parameter info is not available");
            throw new InternalServerErrorException();
        }
        Object instance;
        try {
            instance = ClassLoaderUtils.loadClass(clazz.getName(), JAXRSUtils.class).newInstance();
        } catch (Throwable t) {
            throw new InternalServerErrorException(t);
        }
        JAXRSUtils.injectParameters(ori, bmi, instance, m);
        
        InjectionUtils.injectContexts(instance, bmi, m);
        
        return instance;
    }
    
    public static <T> T createContextValue(Message m, Type genericType, Class<T> clazz) {
 
        Message contextMessage = m.getExchange() != null ? m.getExchange().getInMessage() : m;
        if (contextMessage == null && Boolean.FALSE.equals(m.get(Message.INBOUND_MESSAGE))) {
            contextMessage = m;
        }
        Object o = null;
        if (UriInfo.class.isAssignableFrom(clazz)) {
            o = createUriInfo(contextMessage);
        } else if (HttpHeaders.class.isAssignableFrom(clazz)
            || ProtocolHeaders.class.isAssignableFrom(clazz)) {
            o = createHttpHeaders(contextMessage, clazz);
        } else if (SecurityContext.class.isAssignableFrom(clazz)) {
            SecurityContext customContext = contextMessage.get(SecurityContext.class);
            o = customContext == null ? new SecurityContextImpl(contextMessage) : customContext;
        } else if (MessageContext.class.isAssignableFrom(clazz)) {
            o = new MessageContextImpl(m);
        } else if (ResourceInfo.class.isAssignableFrom(clazz)) {
            o = new ResourceInfoImpl(contextMessage);
        } else if (Request.class.isAssignableFrom(clazz)) {
            o = new RequestImpl(contextMessage);
        } else if (Providers.class.isAssignableFrom(clazz)) {
            o = new ProvidersImpl(contextMessage);
        } else if (ContextResolver.class.isAssignableFrom(clazz)) {
            o = createContextResolver(genericType, contextMessage);
        } else if (Application.class.isAssignableFrom(clazz)) {
            ProviderInfo<?> providerInfo = 
                (ProviderInfo<?>)contextMessage.getExchange().getEndpoint().get(Application.class.getName());
            o = providerInfo == null ? null : providerInfo.getProvider();
        }
        if (o == null && contextMessage != null && !MessageUtils.isRequestor(contextMessage)) {
            o = createServletResourceValue(contextMessage, clazz);
            if (o == null) {
                ContextProvider<?> provider = 
                    ProviderFactory.getInstance(m).createContextProvider(clazz, contextMessage);
                if (provider != null) {
                    o = provider.createContext(contextMessage);
                }
            }
        }
        return clazz.cast(o);
    }
    
    @SuppressWarnings("unchecked")
    private static UriInfo createUriInfo(Message m) {
        if (MessageUtils.isRequestor(m)) {
            m = m.getExchange() != null ? m.getExchange().getOutMessage() : m;
        }
        MultivaluedMap<String, String> templateParams =
            (MultivaluedMap<String, String>)m.get(URITemplate.TEMPLATE_PARAMETERS);
        return new UriInfoImpl(m, templateParams);
    }
    
    private static Object createHttpHeaders(Message m, Class<?> ctxClass) {
        if (MessageUtils.isRequestor(m)) {
            m = m.getExchange() != null ? m.getExchange().getOutMessage() : m;
        }
        return HttpHeaders.class.isAssignableFrom(ctxClass) ? new HttpHeadersImpl(m)
            : new ProtocolHeadersImpl(m);
    }
    
    public static ContextResolver<?> createContextResolver(Type genericType, Message m) {
        if (genericType instanceof ParameterizedType) {
            return ProviderFactory.getInstance(m).createContextResolver(
                      ((ParameterizedType)genericType).getActualTypeArguments()[0], m);
        } else if (m != null) {
            return ProviderFactory.getInstance(m).createContextResolver(genericType, m);
        } else {
            return null;
        }
    }

    public static Object createResourceValue(Message m, Type genericType, Class<?> clazz) {
                
        // lets assume we're aware of servlet types only that can be @Resource-annotated
        return createContextValue(m, genericType, clazz);
    }
    
    public static <T> T createServletResourceValue(Message m, Class<T> clazz) {
        
        Object value = null; 
        if (HttpServletRequest.class.isAssignableFrom(clazz)) {
            value = m.get(AbstractHTTPDestination.HTTP_REQUEST);
        }
        if (HttpServletResponse.class.isAssignableFrom(clazz)) {
            HttpServletResponse response = (HttpServletResponse)m.get(AbstractHTTPDestination.HTTP_RESPONSE);
            value = response != null ? new HttpServletResponseFilter(response, m) : null;
        }
        if (ServletContext.class.isAssignableFrom(clazz)) {
            value = m.get(AbstractHTTPDestination.HTTP_CONTEXT);
        }
        if (ServletConfig.class.isAssignableFrom(clazz)) {
            value = m.get(AbstractHTTPDestination.HTTP_CONFIG);
        }
        
        return clazz.cast(value);
    }
    //CHECKSTYLE:OFF
    private static Object readFromUriParam(Message m,
                                           String parameterName,
                                           Class<?> paramType,
                                           Type genericType,
                                           Annotation[] paramAnns,
                                           MultivaluedMap<String, String> values,
                                           String defaultValue,
                                           boolean decoded) {
    //CHECKSTYLE:ON    
        if ("".equals(parameterName)) {
            return InjectionUtils.handleBean(paramType, paramAnns, values, ParameterType.PATH, m, decoded);
        } else {
            List<String> results = values.get(parameterName);
            return InjectionUtils.createParameterObject(results, 
                                                    paramType, 
                                                    genericType,
                                                    paramAnns,
                                                    defaultValue,
                                                    decoded,
                                                    ParameterType.PATH,
                                                    m);
        }
    }
    
    
    
    //TODO : multiple query string parsing, do it once
    private static Object readQueryString(String queryName,
                                          Class<?> paramType,
                                          Type genericType,
                                          Annotation[] paramAnns,
                                          Message m, 
                                          String defaultValue,
                                          boolean decode) {
        
        MultivaluedMap<String, String> queryMap = new UriInfoImpl(m, null).getQueryParameters(decode);
        
        if ("".equals(queryName)) {
            return InjectionUtils.handleBean(paramType, paramAnns, queryMap, ParameterType.QUERY, m, false);
        } else {
            return InjectionUtils.createParameterObject(queryMap.get(queryName), 
                                                        paramType, 
                                                        genericType,
                                                        paramAnns,
                                                        defaultValue,
                                                        false,
                                                        ParameterType.QUERY, m);
        }
    }

    
    
    /**
     * Retrieve map of query parameters from the passed in message
     * @param message
     * @return a Map of query parameters.
     */
    public static MultivaluedMap<String, String> getStructuredParams(String query, 
                                                                    String sep, 
                                                                    boolean decode,
                                                                    boolean decodePlus) {
        MultivaluedMap<String, String> map = 
            new MetadataMap<String, String>(new LinkedHashMap<String, List<String>>());
        
        getStructuredParams(map, query, sep, decode, decodePlus);
        
        return map;
    }
    
    public static void getStructuredParams(MultivaluedMap<String, String> queries,
                                           String query, 
                                           String sep, 
                                           boolean decode,
                                           boolean decodePlus) {
        if (!StringUtils.isEmpty(query)) {            
            List<String> parts = Arrays.asList(query.split(sep));
            for (String part : parts) {
                int index = part.indexOf('=');
                String name = null;
                String value = null;
                if (index == -1) {
                    name = part;
                    value = "";
                } else {
                    name = part.substring(0, index);
                    value =  index < part.length() ? part.substring(index + 1) : "";
                    if (decode || (decodePlus && value.contains("+"))) {
                        value = (";".equals(sep))
                            ? HttpUtils.pathDecode(value) : HttpUtils.urlDecode(value); 
                    }
                }
                queries.add(HttpUtils.urlDecode(name), value);
            }
        }
    }

    private static Object readFromMessageBody(Class<?> targetTypeClass,
                                                  Type parameterType,
                                                  Annotation[] parameterAnnotations,
                                                  InputStream is, 
                                                  MediaType contentType, 
                                                  List<MediaType> consumeTypes,
                                                  Message m) throws IOException, WebApplicationException {
        
        List<MediaType> types = JAXRSUtils.intersectMimeTypes(consumeTypes, contentType);
        
        final ProviderFactory pf = ProviderFactory.getInstance(m);
        for (MediaType type : types) { 
            List<ReaderInterceptor> readers = pf.createMessageBodyReaderInterceptor(
                                         targetTypeClass,
                                         parameterType,
                                         parameterAnnotations,
                                         type,
                                         m);
            if (readers != null) {
                try {
                    return readFromMessageBodyReader(readers, 
                                                     targetTypeClass, 
                                                     parameterType, 
                                                     parameterAnnotations, 
                                                     is, 
                                                     type,
                                                     m);    
                } catch (IOException e) {
                    throw e;
                } catch (WebApplicationException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new WebApplicationException(ex);
                }
            } else {
                String errorMessage = new org.apache.cxf.common.i18n.Message("NO_MSG_READER",
                                                       BUNDLE,
                                                       targetTypeClass.getSimpleName(),
                                                       contentType).toString();
                LOG.warning(errorMessage);
                throw new WebApplicationException(Response.Status.UNSUPPORTED_MEDIA_TYPE);
            }
        }

        return null;
    }
    
    @SuppressWarnings("unchecked")
    public static Object readFromMessageBodyReader(List<ReaderInterceptor> readers,
                                                   Class<?> targetTypeClass,
                                                   Type parameterType,
                                                   Annotation[] parameterAnnotations,
                                                   InputStream is, 
                                                   MediaType mediaType, 
                                                   Message m) throws IOException, WebApplicationException {
        
        // Verbose but avoids an extra context instantiation for the typical path
        if (readers.size() > 1) {
            ReaderInterceptor first = readers.remove(0);
            ReaderInterceptorContext context = new ReaderInterceptorContextImpl(targetTypeClass, 
                                                                            parameterType, 
                                                                            parameterAnnotations, 
                                                                            mediaType,
                                                                            is,
                                                                            m,
                                                                            readers);
            
            return first.aroundReadFrom(context);
        } else {
            MessageBodyReader<?> provider = ((ReaderInterceptorMBR)readers.get(0)).getMBR();
            @SuppressWarnings("rawtypes")
            Class cls = (Class)targetTypeClass;
            return provider.readFrom(
                      cls, parameterType, parameterAnnotations, mediaType,
                      new HttpHeadersImpl(m).getRequestHeaders(), is);
        }
    }

    
    //CHECKSTYLE:OFF
    public static void writeMessageBody(List<WriterInterceptor> writers, 
                                Object entity,
                                Class<?> type, Type genericType,
                                Annotation[] annotations, 
                                MediaType mediaType,
                                MultivaluedMap<String, Object> httpHeaders,
                                Message message) 
        throws WebApplicationException, IOException {
        
        OutputStream entityStream = message.getContent(OutputStream.class);
        if (writers.size() > 1) {
            WriterInterceptor first = writers.remove(0);
            WriterInterceptorContext context = new WriterInterceptorContextImpl(entity,
                                                                                type, 
                                                                            genericType, 
                                                                            annotations, 
                                                                            mediaType,
                                                                            entityStream,
                                                                            message,
                                                                            writers);
            
            first.aroundWriteTo(context);
        } else {
            MessageBodyWriter<Object> writer = ((WriterInterceptorMBW)writers.get(0)).getMBW();
            writer.writeTo(entity, type, genericType, annotations, mediaType,
                           httpHeaders, entityStream);
        }
    }
    //CHECKSTYLE:ON
    

    public static boolean matchConsumeTypes(MediaType requestContentType, 
                                            OperationResourceInfo ori) {
        
        return intersectMimeTypes(ori.getConsumeTypes(), requestContentType).size() != 0;
    }
    
    public static boolean matchProduceTypes(MediaType acceptContentType, 
                                            OperationResourceInfo ori) {
        
        return intersectMimeTypes(ori.getProduceTypes(), acceptContentType).size() != 0;
    }
    
    public static boolean matchMimeTypes(MediaType requestContentType, 
                                         MediaType acceptContentType, 
                                         OperationResourceInfo ori) {
        
        if (intersectMimeTypes(ori.getConsumeTypes(), requestContentType).size() != 0
            && intersectMimeTypes(ori.getProduceTypes(), acceptContentType).size() != 0) {
            return true;
        }
        return false;
    }

    public static List<MediaType> parseMediaTypes(String types) {
        List<MediaType> acceptValues = new ArrayList<MediaType>();
        
        if (types != null) {
            while (types.length() > 0) {
                String tp = types;
                int index = types.indexOf(',');
                if (index != -1) {
                    tp = types.substring(0, index);
                    types = types.substring(index + 1).trim();
                } else {
                    types = "";
                }
                acceptValues.add(MediaType.valueOf(tp));
            }
        } else {
            acceptValues.add(ALL_TYPES);
        }
        
        return acceptValues;
    }
    
    /**
     * intersect two mime types
     * 
     * @param mimeTypesA 
     * @param mimeTypesB 
     * @return return a list of intersected mime types
     */   
    public static List<MediaType> intersectMimeTypes(List<MediaType> requiredMediaTypes, 
                                                     List<MediaType> userMediaTypes,
                                                     boolean addRequiredParamsIfPossible) {
        Set<MediaType> supportedMimeTypeList = new LinkedHashSet<MediaType>();

        for (MediaType requiredType : requiredMediaTypes) {
            for (MediaType userType : userMediaTypes) {
                boolean isCompatible = 
                    requiredType.isCompatible(userType) || userType.isCompatible(requiredType);
                if (!isCompatible && requiredType.getType().equalsIgnoreCase(userType.getType())) {
                    // check if we have composite subtypes
                    String subType1 = requiredType.getSubtype();
                    String subType2 = userType.getSubtype();
                    
                    String subTypeAfterPlus1 = splitMediaSubType(subType1, true); 
                    String subTypeAfterPlus2 = splitMediaSubType(subType2, true);
                    
                    if (subTypeAfterPlus1 != null && subTypeAfterPlus2 != null) {
                    
                        isCompatible = subTypeAfterPlus1.equalsIgnoreCase(subTypeAfterPlus2)
                            && (subType1.charAt(0) == '*' || subType2.charAt(0) == '*');
                        
                        if (!isCompatible) {
                            String subTypeBeforePlus1 = splitMediaSubType(subType1, false);
                            String subTypeBeforePlus2 = splitMediaSubType(subType2, false);
                            
                            isCompatible = subTypeBeforePlus1.equalsIgnoreCase(subTypeBeforePlus2)
                                && (subType1.charAt(subType1.length() - 1) == '*' 
                                    || subType2.charAt(subType2.length() - 1) == '*');
                        }
                    }
                }
                if (isCompatible) {
                    boolean parametersMatched = true;
                    for (Map.Entry<String, String> entry : userType.getParameters().entrySet()) {
                        String value = requiredType.getParameters().get(entry.getKey());
                        if (value != null && !value.equals(entry.getValue())) {
                            parametersMatched = false;
                            break;
                        }
                    }
                    if (!parametersMatched) {
                        continue;
                    }
                   
                    String type = requiredType.getType().equals(MediaType.MEDIA_TYPE_WILDCARD) 
                                      ? userType.getType() : requiredType.getType();
                    String subtype = requiredType.getSubtype().startsWith(MediaType.MEDIA_TYPE_WILDCARD) 
                                      ? userType.getSubtype() : requiredType.getSubtype();
                    Map<String, String> parameters = userType.getParameters();
                    if (addRequiredParamsIfPossible) {
                        parameters = new LinkedHashMap<String, String>(parameters);
                        for (Map.Entry<String, String> entry : requiredType.getParameters().entrySet()) {
                            if (!parameters.containsKey(entry.getKey())) {
                                parameters.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    supportedMimeTypeList.add(new MediaType(type, subtype, parameters));
                }
            }
        }

        return new ArrayList<MediaType>(supportedMimeTypeList);
        
    }
    
    private static String splitMediaSubType(String type, boolean after) {
        int index = type.indexOf('+');
        return index == -1 ? null : after ? type.substring(index + 1) : type.substring(0, index);
    }
    
    public static List<MediaType> intersectMimeTypes(List<MediaType> mimeTypesA, 
                                                     MediaType mimeTypeB) {
        return intersectMimeTypes(mimeTypesA, 
                                  Collections.singletonList(mimeTypeB), false);
    }
    
    public static List<MediaType> intersectMimeTypes(String mimeTypesA, 
                                                     String mimeTypesB) {
        return intersectMimeTypes(parseMediaTypes(mimeTypesA),
                                  parseMediaTypes(mimeTypesB),
                                  false);
    }
    
    public static List<MediaType> sortMediaTypes(String mediaTypes) {
        return sortMediaTypes(JAXRSUtils.parseMediaTypes(mediaTypes));
    }
    
    public static List<MediaType> sortMediaTypes(List<MediaType> types) {
        if (types.size() > 1) {
            Collections.sort(types, new Comparator<MediaType>() {

                public int compare(MediaType mt1, MediaType mt2) {
                    return JAXRSUtils.compareMediaTypes(mt1, mt2);
                }
                
            });
        }
        return types;
    }
    
    public static Class<?> getWebApplicationExceptionClass(Response exResponse,
                                                           Class<?> defaultExceptionType) {
        int status = exResponse.getStatus();
        Class<?> cls = EXCEPTIONS_MAP.get(status);
        if (cls == null) {
            int family = status / 100;
            if (family == 3) {
                cls = RedirectionException.class;
            } else if (family == 4) {
                cls = ClientErrorException.class;
            } else if (family == 5) {
                cls = ServerErrorException.class;
            }
        }
        return cls == null ? defaultExceptionType : cls;
    }
    
    public static <T extends Throwable> Response convertFaultToResponse(T ex, Message inMessage) {
        ExceptionMapper<T>  mapper =
            ProviderFactory.getInstance(inMessage).createExceptionMapper(ex.getClass(), inMessage);
        if (mapper != null) {
            try {
                return mapper.toResponse(ex);
            } catch (Exception mapperEx) {
                mapperEx.printStackTrace();
                return Response.serverError().build();
            }
        }
        return null;
        
    }
    
    public static String removeMediaTypeParameter(MediaType mt, String paramName) {
        StringBuilder sb = new StringBuilder();
        sb.append(mt.getType()).append('/').append(mt.getSubtype());
        if (mt.getParameters().size() > 1) {
            for (String key : mt.getParameters().keySet()) {
                if (!paramName.equals(key)) {
                    sb.append(';').append(key).append('=').append(mt.getParameters().get(key));
                }
            }
        }    
        return sb.toString();
    }
        
    public static boolean propogateException(Message m) {
        
        Object value = m.getContextualProperty(PROPAGATE_EXCEPTION);
        
        if (value == null) {
            return true;
        }

        if (Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(value.toString())) {
            return true;
        }
        
        return false;
    }
    
    public static QName getClassQName(Class<?> type) {
        String nsURI = PackageUtils.getNamespace(PackageUtils.getPackageName(type));
        if (nsURI.endsWith("/")) {
            nsURI = nsURI.substring(0, nsURI.length() - 1);
        }
        return new QName(nsURI, type.getSimpleName(), "ns1"); 
    }

    public static QName convertStringToQName(String name) {
        return XMLUtils.convertStringToQName(name, "");
    }
    
    public static boolean runContainerRequestFilters(ProviderFactory pf, Message m, boolean preMatch, 
                                              List<String> names) {
        List<ProviderInfo<ContainerRequestFilter>> containerFilters = preMatch 
            ? pf.getPreMatchContainerRequestFilters() : pf.getPostMatchContainerRequestFilters(names);
        if (!containerFilters.isEmpty()) {
            ContainerRequestContext context = new ContainerRequestContextImpl(m, preMatch, false);
            for (ProviderInfo<ContainerRequestFilter> filter : containerFilters) {
                try {
                    filter.getProvider().filter(context);
                } catch (IOException ex) {
                    throw new InternalServerErrorException(ex);
                }
                if (m.getExchange().get(Response.class) != null) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public static void runContainerResponseFilters(ProviderFactory pf,
                                                   Response r,
                                                   Message m, 
                                                   OperationResourceInfo ori) {
        List<ProviderInfo<ContainerResponseFilter>> containerFilters =  
            pf.getContainerResponseFilters(ori == null ? null : ori.getNameBindings());
        if (!containerFilters.isEmpty()) {
            ContainerRequestContext requestContext = 
                new ContainerRequestContextImpl(m.getExchange().getInMessage(), 
                                               false,
                                               true);
            ContainerResponseContext responseContext = 
                new ContainerResponseContextImpl(r, m, ori);
            for (ProviderInfo<ContainerResponseFilter> filter : containerFilters) {
                try {
                    InjectionUtils.injectContexts(filter.getProvider(), filter, m);
                    filter.getProvider().filter(requestContext, responseContext);
                } catch (IOException ex) {
                    throw new WebApplicationException(ex);
                }
            }
        }
    }
}
