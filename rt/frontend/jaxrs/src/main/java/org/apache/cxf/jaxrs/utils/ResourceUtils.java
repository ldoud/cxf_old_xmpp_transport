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

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.BeanParam;
import javax.ws.rs.CookieParam;
import javax.ws.rs.Encoded;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.jaxb.JAXBUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.PerRequestResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.MethodDispatcher;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.model.Parameter;
import org.apache.cxf.jaxrs.model.ParameterType;
import org.apache.cxf.jaxrs.model.ResourceTypes;
import org.apache.cxf.jaxrs.model.URITemplate;
import org.apache.cxf.jaxrs.model.UserOperation;
import org.apache.cxf.jaxrs.model.UserResource;
import org.apache.cxf.jaxrs.model.wadl.ElementClass;
import org.apache.cxf.jaxrs.model.wadl.XMLName;
import org.apache.cxf.jaxrs.provider.JAXBElementProvider;
import org.apache.cxf.message.Message;
import org.apache.cxf.resource.ResourceManager;

public final class ResourceUtils {
    
    private static final Logger LOG = LogUtils.getL7dLogger(ResourceUtils.class);
    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(ResourceUtils.class);
    private static final String CLASSPATH_PREFIX = "classpath:";
    
    private ResourceUtils() {
        
    }
    
    public static Method findPostConstructMethod(Class<?> c) {
        if (Object.class == c || null == c) {
            return null;
        }
        for (Method m : c.getDeclaredMethods()) {
            if (m.getAnnotation(PostConstruct.class) != null) {
                return m;
            }
        }
        Method m = findPostConstructMethod(c.getSuperclass());
        if (m != null) {
            return m;
        }
        for (Class<?> i : c.getInterfaces()) {
            m = findPostConstructMethod(i);
            if (m != null) {
                return m;
            }
        }
        return null;
    }
    
    public static Method findPreDestroyMethod(Class<?> c) {
        if (Object.class == c || null == c) {
            return null;
        }
        for (Method m : c.getDeclaredMethods()) {
            if (m.getAnnotation(PreDestroy.class) != null) {
                return m;
            }
        }
        Method m = findPreDestroyMethod(c.getSuperclass());
        if (m != null) {
            return m;
        }
        for (Class<?> i : c.getInterfaces()) {
            m = findPreDestroyMethod(i);
            if (m != null) {
                return m;
            }
        }
        return null;
    }
    
    public static ClassResourceInfo createClassResourceInfo(Map<String, UserResource> resources, 
                                                            UserResource model, boolean isRoot, boolean enableStatic) {
        return createClassResourceInfo(resources, model, isRoot, enableStatic, 
                                       BusFactory.getThreadDefaultBus());
    }
    
    public static ClassResourceInfo createClassResourceInfo(
        Map<String, UserResource> resources, UserResource model, boolean isRoot, boolean enableStatic,
        Bus bus) {
        
        Class<?> sClass = loadClass(model.getName());
        return createServiceClassResourceInfo(resources, model, sClass, isRoot, enableStatic, bus);
    }
    
    public static ClassResourceInfo createServiceClassResourceInfo(
        Map<String, UserResource> resources, UserResource model, 
        Class<?> sClass, boolean isRoot, boolean enableStatic, Bus bus) {
        if (model == null) {
            throw new RuntimeException("Resource class " + sClass.getName() + " has no model info");
        }
        ClassResourceInfo cri = 
            new ClassResourceInfo(sClass, sClass, isRoot, enableStatic, true,
                                  model.getConsumes(), model.getProduces(), bus);
        URITemplate t = URITemplate.createTemplate(model.getPath());
        cri.setURITemplate(t);
        MethodDispatcher md = new MethodDispatcher();
        Map<String, UserOperation> ops = model.getOperationsAsMap();
        for (Method m : cri.getServiceClass().getMethods()) {
            UserOperation op = ops.get(m.getName());
            if (op == null || op.getName() == null) {
                continue;
            }
            OperationResourceInfo ori = 
                new OperationResourceInfo(m, cri, URITemplate.createTemplate(op.getPath()),
                                          op.getVerb(), op.getConsumes(), op.getProduces(),
                                          op.getParameters(),
                                          op.isOneway());
            String rClassName = m.getReturnType().getName();
            if (op.getVerb() == null) {
                if (resources.containsKey(rClassName)) {
                    ClassResourceInfo subCri = rClassName.equals(model.getName()) ? cri 
                        : createServiceClassResourceInfo(resources, resources.get(rClassName),
                                                         m.getReturnType(), false, enableStatic, bus);
                    if (subCri != null) {
                        cri.addSubClassResourceInfo(subCri);
                        md.bind(ori, m);
                    }
                }
            } else {
                md.bind(ori, m);
            }
        }
        cri.setMethodDispatcher(md);
        return checkMethodDispatcher(cri) ? cri : null;

    }
    
    public static ClassResourceInfo createClassResourceInfo(final Class<?> rClass, 
                                                            final Class<?> sClass,
                                                            boolean root, 
                                                            boolean enableStatic) {
        return createClassResourceInfo(rClass, sClass, root, enableStatic, BusFactory.getThreadDefaultBus());
        
    }
    
    public static ClassResourceInfo createClassResourceInfo(final Class<?> rClass, 
                                                            final Class<?> sClass,
                                                            boolean root, 
                                                            boolean enableStatic,
                                                            Bus bus) {
        ClassResourceInfo cri = new ClassResourceInfo(rClass, sClass, root, enableStatic, bus);

        if (root) {
            URITemplate t = URITemplate.createTemplate(cri.getPath());
            cri.setURITemplate(t);
        }
        
        evaluateResourceClass(cri, enableStatic);
        return checkMethodDispatcher(cri) ? cri : null;
    }

    private static void evaluateResourceClass(ClassResourceInfo cri, boolean enableStatic) {
        MethodDispatcher md = new MethodDispatcher();
        for (Method m : cri.getServiceClass().getMethods()) {
            
            Method annotatedMethod = AnnotationUtils.getAnnotatedMethod(m);
            
            String httpMethod = AnnotationUtils.getHttpMethodValue(annotatedMethod);
            Path path = AnnotationUtils.getMethodAnnotation(annotatedMethod, Path.class);
            
            if (httpMethod != null || path != null) {
                md.bind(createOperationInfo(m, annotatedMethod, cri, path, httpMethod), m);
                if (httpMethod == null) {
                    // subresource locator
                    Class<?> subClass = m.getReturnType();
                    if (enableStatic) {
                        ClassResourceInfo subCri = cri.findResource(subClass, subClass);
                        if (subCri == null) {
                            subCri = subClass == cri.getServiceClass() ? cri
                                     : createClassResourceInfo(subClass, subClass, false, enableStatic,
                                                               cri.getBus());
                        }
                        
                        if (subCri != null) {
                            cri.addSubClassResourceInfo(subCri);
                        }
                    }
                }
            }
        }
        cri.setMethodDispatcher(md);
    }
    
    public static Constructor<?> findResourceConstructor(Class<?> resourceClass, boolean perRequest) {
        List<Constructor<?>> cs = new LinkedList<Constructor<?>>();
        for (Constructor<?> c : resourceClass.getConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            Annotation[][] anns = c.getParameterAnnotations();
            boolean match = true;
            for (int i = 0; i < params.length; i++) {
                if (!perRequest) { 
                    if (AnnotationUtils.getAnnotation(anns[i], Context.class) == null) {
                        match = false;
                        break;
                    }
                } else if (!AnnotationUtils.isValidParamAnnotations(anns[i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                cs.add(c);
            }
        }
        Collections.sort(cs, new Comparator<Constructor<?>>() {

            public int compare(Constructor<?> c1, Constructor<?> c2) {
                int p1 = c1.getParameterTypes().length;
                int p2 = c2.getParameterTypes().length;
                return p1 > p2 ? -1 : p1 < p2 ? 1 : 0;
            }
        
        });
        return cs.size() == 0 ? null : cs.get(0);
    }
    
    public static List<Parameter> getParameters(Method resourceMethod) {
        Annotation[][] paramAnns = resourceMethod.getParameterAnnotations();
        if (paramAnns.length == 0) {
            return CastUtils.cast(Collections.emptyList(), Parameter.class);
        }
        Class<?>[] types = resourceMethod.getParameterTypes();
        List<Parameter> params = new ArrayList<Parameter>(paramAnns.length);
        for (int i = 0; i < paramAnns.length; i++) {
            Parameter p = getParameter(i, paramAnns[i], types[i]);
            params.add(p);
        }
        return params;
    }
    
    //CHECKSTYLE:OFF
    public static Parameter getParameter(int index, Annotation[] anns, Class<?> type) {
        
        Context ctx = AnnotationUtils.getAnnotation(anns, Context.class);
        if (ctx != null) {
            return new Parameter(ParameterType.CONTEXT, index, null);
        }
        
        boolean isEncoded = AnnotationUtils.getAnnotation(anns, Encoded.class) != null;
        
        BeanParam bp = AnnotationUtils.getAnnotation(anns, BeanParam.class);
        if (bp != null) {
            return new Parameter(ParameterType.BEAN, index, null, isEncoded, null);
        }
        
        String dValue = AnnotationUtils.getDefaultParameterValue(anns);
        
        PathParam a = AnnotationUtils.getAnnotation(anns, PathParam.class); 
        if (a != null) {
            return new Parameter(ParameterType.PATH, index, a.value(), isEncoded, dValue);
        } 
        QueryParam q = AnnotationUtils.getAnnotation(anns, QueryParam.class);
        if (q != null) {
            return new Parameter(ParameterType.QUERY, index, q.value(), isEncoded, dValue);
        }
        MatrixParam m = AnnotationUtils.getAnnotation(anns, MatrixParam.class);
        if (m != null) {
            return new Parameter(ParameterType.MATRIX, index, m.value(), isEncoded, dValue);
        }  
    
        FormParam f = AnnotationUtils.getAnnotation(anns, FormParam.class);
        if (f != null) {
            return new Parameter(ParameterType.FORM, index, f.value(), isEncoded, dValue);
        }
        
        HeaderParam h = AnnotationUtils.getAnnotation(anns, HeaderParam.class);
        if (h != null) {
            return new Parameter(ParameterType.HEADER, index, h.value(), isEncoded, dValue);
        }  
        
        CookieParam c = AnnotationUtils.getAnnotation(anns, CookieParam.class);
        if (c != null) {
            return new Parameter(ParameterType.COOKIE, index, c.value(), isEncoded, dValue);
        }
        
        return new Parameter(ParameterType.REQUEST_BODY, index, null); 
        
    }
    //CHECKSTYLE:ON
    
    private static OperationResourceInfo createOperationInfo(Method m, Method annotatedMethod, 
                                                      ClassResourceInfo cri, Path path, String httpMethod) {
        OperationResourceInfo ori = new OperationResourceInfo(m, annotatedMethod, cri);
        URITemplate t = URITemplate.createTemplate(path);
        ori.setURITemplate(t);
        ori.setHttpMethod(httpMethod);
        return ori;
    }
    
       
    private static boolean checkMethodDispatcher(ClassResourceInfo cr) {
        if (cr.getMethodDispatcher().getOperationResourceInfos().isEmpty()) {
            LOG.warning(new org.apache.cxf.common.i18n.Message("NO_RESOURCE_OP_EXC", 
                                                               BUNDLE, 
                                                               cr.getServiceClass().getName()).toString());
            return false;
        }
        return true;
    }
    

    private static Class<?> loadClass(String cName) {
        try {
            return ClassLoaderUtils.loadClass(cName.trim(), ResourceUtils.class);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("No class " + cName.trim() + " can be found", ex); 
        }
    }
    
    
    public static List<UserResource> getUserResources(String loc, Bus bus) {
        try {
            InputStream is = ResourceUtils.getResourceStream(loc, bus);
            if (is == null) {
                return null;
            }
            return getUserResources(is);
        } catch (Exception ex) {
            LOG.warning("Problem with processing a user model at " + loc);
        }
        
        return null;
    }
    
    public static InputStream getResourceStream(String loc, Bus bus) throws Exception {
        URL url = getResourceURL(loc, bus);
        return url == null ? null : url.openStream();
    }
    
    public static URL getResourceURL(String loc, Bus bus) throws Exception {
        URL url = null;
        if (loc.startsWith(CLASSPATH_PREFIX)) {
            String path = loc.substring(CLASSPATH_PREFIX.length());
            url = ResourceUtils.getClasspathResourceURL(path, ResourceUtils.class, bus);
        } else {
            try {
                url = new URL(loc);
            } catch (Exception ex) {
                // it can be either a classpath or file resource without a scheme
                url = ResourceUtils.getClasspathResourceURL(loc, ResourceUtils.class, bus);
                if (url == null) {
                    File file = new File(loc);
                    if (file.exists()) {
                        url = file.toURI().toURL();
                    }
                }
            }
        }
        if (url == null) {
            LOG.warning("No resource " + loc + " is available");
        }
        return url;
    }
    
    public static InputStream getClasspathResourceStream(String path, Class<?> callingClass, Bus bus) {
        InputStream is = ClassLoaderUtils.getResourceAsStream(path, callingClass);
        return is == null ? getResource(path, InputStream.class, bus) : is;
    }
    
    public static URL getClasspathResourceURL(String path, Class<?> callingClass, Bus bus) {
        URL url = ClassLoaderUtils.getResource(path, callingClass);
        return url == null ? getResource(path, URL.class, bus) : url;
    }
    
    public static <T> T getResource(String path, Class<T> resourceClass, Bus bus) {
        if (bus != null) {
            ResourceManager rm = bus.getExtension(ResourceManager.class);
            if (rm != null) {
                return rm.resolveResource(path, resourceClass);
            }
        }
        return null;
    }
    
    public static List<UserResource> getUserResources(String loc) {
        return getUserResources(loc, BusFactory.getThreadDefaultBus());
    }
    
    public static List<UserResource> getUserResources(InputStream is) throws Exception {
        Document doc = DOMUtils.readXml(new InputStreamReader(is, "UTF-8"));
        return getResourcesFromElement(doc.getDocumentElement());
    }
    
    public static List<UserResource> getResourcesFromElement(Element modelEl) {
        List<UserResource> resources = new ArrayList<UserResource>();
        List<Element> resourceEls = 
            DOMUtils.findAllElementsByTagNameNS(modelEl, 
                                                "http://cxf.apache.org/jaxrs", "resource");
        for (Element e : resourceEls) {
            resources.add(getResourceFromElement(e));
        }
        return resources;
    }
    

    public static ResourceTypes getAllRequestResponseTypes(List<ClassResourceInfo> cris, 
                                                           boolean jaxbOnly) {
        ResourceTypes types = new ResourceTypes();
        for (ClassResourceInfo resource : cris) {
            getAllTypesForResource(resource, types, jaxbOnly);
        }
        return types;
    }

    public static Class<?> getActualJaxbType(Class<?> type, Method resourceMethod, boolean inbound) {
        ElementClass element = resourceMethod.getAnnotation(ElementClass.class);
        if  (element != null) {
            Class<?> cls = inbound ? element.request() : element.response();
            if (cls != Object.class) {
                return cls;
            }
        }
        return type;
    }
    
    private static void getAllTypesForResource(ClassResourceInfo resource, 
                                               ResourceTypes types,
                                               boolean jaxbOnly) {
        for (OperationResourceInfo ori : resource.getMethodDispatcher().getOperationResourceInfos()) {
            Method method = ori.getMethodToInvoke();
            Class<?> realReturnType = method.getReturnType();
            Class<?> cls = realReturnType;
            if (cls == Response.class) {
                cls = getActualJaxbType(cls, method, false);
            }
            Type type = method.getGenericReturnType();
            if (jaxbOnly) {
                checkJaxbType(cls, realReturnType == Response.class ? cls : type, types, 
                    method.getAnnotations());
            } else {
                types.getAllTypes().put(cls, type);
            }
            
            for (Parameter pm : ori.getParameters()) {
                if (pm.getType() == ParameterType.REQUEST_BODY) {
                    Class<?> inType = method.getParameterTypes()[pm.getIndex()];
                    Type paramType = method.getGenericParameterTypes()[pm.getIndex()];
                    if (jaxbOnly) {
                        checkJaxbType(inType, paramType, types, 
                                      method.getParameterAnnotations()[pm.getIndex()]);
                    } else {
                        types.getAllTypes().put(inType, paramType);
                    }
                    
                }
            }
            
        }
        
        for (ClassResourceInfo sub : resource.getSubResources()) {
            if (sub != resource) {
                getAllTypesForResource(sub, types, jaxbOnly);
            }
        }
    }
    
    private static void checkJaxbType(Class<?> type, 
                                      Type genericType, 
                                      ResourceTypes types,
                                      Annotation[] anns) {
        if (InjectionUtils.isSupportedCollectionOrArray(type)) {
            type = InjectionUtils.getActualType(genericType);
            XMLName name = AnnotationUtils.getAnnotation(anns, XMLName.class);
            if (name != null) {
                types.getCollectionMap().put(type, JAXRSUtils.convertStringToQName(name.value()));
            }
        }
        JAXBElementProvider<?> provider = new JAXBElementProvider<Object>();
        if (type != null 
            && !InjectionUtils.isPrimitive(type) 
            && !JAXBElement.class.isAssignableFrom(type)
            && provider.isReadable(type, type, new Annotation[0], MediaType.APPLICATION_XML_TYPE)) {
            types.getAllTypes().put(type, type);
            
            Class<?> genCls = InjectionUtils.getActualType(genericType);
            if (genCls != type && genCls != null && genCls != Object.class 
                && !InjectionUtils.isSupportedCollectionOrArray(genCls)) {
                types.getAllTypes().put(genCls, genCls);
            }
        }
    }
    
    private static UserResource getResourceFromElement(Element e) {
        UserResource resource = new UserResource();
        resource.setName(e.getAttribute("name"));
        resource.setPath(e.getAttribute("path"));
        resource.setConsumes(e.getAttribute("consumes"));
        resource.setProduces(e.getAttribute("produces"));
        List<Element> operEls = 
            DOMUtils.findAllElementsByTagNameNS(e, 
                 "http://cxf.apache.org/jaxrs", "operation");
        List<UserOperation> opers = new ArrayList<UserOperation>(operEls.size());
        for (Element operEl : operEls) {
            opers.add(getOperationFromElement(operEl));
        }
        resource.setOperations(opers);
        return resource;
    }
    
    private static UserOperation getOperationFromElement(Element e) {
        UserOperation op = new UserOperation();
        op.setName(e.getAttribute("name"));
        op.setVerb(e.getAttribute("verb"));
        op.setPath(e.getAttribute("path"));
        op.setOneway(Boolean.parseBoolean(e.getAttribute("oneway")));
        op.setConsumes(e.getAttribute("consumes"));
        op.setProduces(e.getAttribute("produces"));
        List<Element> paramEls = 
            DOMUtils.findAllElementsByTagNameNS(e, 
                 "http://cxf.apache.org/jaxrs", "param");
        List<Parameter> params = new ArrayList<Parameter>(paramEls.size());
        for (int i = 0; i < paramEls.size(); i++) {
            Element paramEl = paramEls.get(i);
            Parameter p = new Parameter(paramEl.getAttribute("type"), i, paramEl.getAttribute("name"));
            p.setEncoded(Boolean.valueOf(paramEl.getAttribute("encoded")));
            p.setDefaultValue(paramEl.getAttribute("defaultValue"));
            params.add(p);
        }
        op.setParameters(params);
        return op;
    }
    
    public static Object[] createConstructorArguments(Constructor<?> c, Message m) {
        Class<?>[] params = c.getParameterTypes();
        Annotation[][] anns = c.getParameterAnnotations();
        Type[] genericTypes = c.getGenericParameterTypes();
        @SuppressWarnings("unchecked")
        MultivaluedMap<String, String> templateValues = m == null ? null
            : (MultivaluedMap<String, String>)m.get(URITemplate.TEMPLATE_PARAMETERS);
        Object[] values = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            if (AnnotationUtils.getAnnotation(anns[i], Context.class) != null) {
                values[i] = JAXRSUtils.createContextValue(m, genericTypes[i], params[i]);
            } else {
                Parameter p = ResourceUtils.getParameter(i, anns[i], params[i]);
                values[i] = JAXRSUtils.createHttpParameterValue(
                                p, params[i], genericTypes[i], anns[i], m, templateValues, null);
            }
        }
        return values;
    }
    public static JAXRSServerFactoryBean createApplication(Application app, boolean ignoreAppPath) {
        return createApplication(app, ignoreAppPath, false);
    }
    public static JAXRSServerFactoryBean createApplication(Application app, boolean ignoreAppPath,
                                                           boolean staticSubresourceResolution) {
        
        Set<Object> singletons = app.getSingletons();
        verifySingletons(singletons);
        
        List<Class<?>> resourceClasses = new ArrayList<Class<?>>();
        List<Object> providers = new ArrayList<Object>();
        Map<Class<?>, ResourceProvider> map = new HashMap<Class<?>, ResourceProvider>();
        
        // Note, app.getClasse() returns a list of per-resource classes
        // or singleton provider classes
        for (Class<?> c : app.getClasses()) {
            if (isValidApplicationClass(c, singletons)) {
                if (c.getAnnotation(Provider.class) != null) {
                    try {
                        providers.add(c.newInstance());
                    } catch (Throwable ex) {
                        throw new RuntimeException("Provider " + c.getName() + " can not be created", ex); 
                    }
                } else {
                    resourceClasses.add(c);
                    map.put(c, new PerRequestResourceProvider(c));
                }
            }
        }
        
        // we can get either a provider or resource class here        
        for (Object o : singletons) {
            boolean isProvider = o.getClass().getAnnotation(Provider.class) != null;
            if (isProvider) {
                providers.add(o);
            } else {
                resourceClasses.add(o.getClass());
                map.put(o.getClass(), new SingletonResourceProvider(o));
            }
        }
        
        JAXRSServerFactoryBean bean = new JAXRSServerFactoryBean();
        String address = "/";
        if (!ignoreAppPath) {
            ApplicationPath appPath = app.getClass().getAnnotation(ApplicationPath.class);
            if (appPath != null) {
                address = appPath.value().length() == 0 ? "/" : appPath.value();
            }
        }
        bean.setAddress(address);
        bean.setStaticSubresourceResolution(staticSubresourceResolution);
        bean.setResourceClasses(resourceClasses);
        bean.setProviders(providers);
        for (Map.Entry<Class<?>, ResourceProvider> entry : map.entrySet()) {
            bean.setResourceProvider(entry.getKey(), entry.getValue());
        }
        bean.setApplication(app);
        
        return bean;
    }
    
    private static void verifySingletons(Set<Object> singletons) {
        if (singletons.isEmpty()) {
            return;
        }
        Set<String> map = new HashSet<String>(); 
        for (Object s : singletons) {
            if (map.contains(s.getClass().getName())) {
                throw new RuntimeException("More than one instance of the same singleton class "
                                           + s.getClass().getName() + " is available"); 
            } else {
                map.add(s.getClass().getName());
            }
        }
    }
    
    public static boolean isValidResourceClass(Class<?> c) {
        if (c.isInterface() || Modifier.isAbstract(c.getModifiers())) {
            LOG.info("Ignoring invalid resource class " + c.getName());
            return false;
        }
        return true;
    }
    
    private static boolean isValidApplicationClass(Class<?> c, Set<Object> singletons) {
        if (!isValidResourceClass(c)) {
            return false;
        }
        for (Object s : singletons) {
            if (c == s.getClass()) {
                LOG.info("Ignoring per-request resource class " + c.getName() 
                         + " as it is also registered as singleton");
                return false;
            }
        }
        return true;
    }
    
    //TODO : consider moving JAXBDataBinding.createContext to JAXBUtils
    public static JAXBContext createJaxbContext(Set<Class<?>> classes, Class<?>[] extraClass, 
                                          Map<String, Object> contextProperties) {
        if (classes == null || classes.isEmpty()) {
            return null;
        }
        JAXBUtils.scanPackages(classes, extraClass, null);

        JAXBContext ctx;
        try {
            ctx = JAXBContext.newInstance(classes.toArray(new Class[classes.size()]),
                                          contextProperties);
            return ctx;
        } catch (JAXBException ex) {
            LOG.fine("No JAXB context can be created");
        }
        return null;
    }
                                         
}
