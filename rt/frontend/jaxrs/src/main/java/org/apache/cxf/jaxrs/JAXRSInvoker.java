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

package org.apache.cxf.jaxrs;


import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.classloader.ClassLoaderUtils.ClassLoaderHolder;
import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.ClassHelper;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.InterceptorChain.State;
import org.apache.cxf.jaxrs.impl.AsyncResponseImpl;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.MethodInvocationInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfoStack;
import org.apache.cxf.jaxrs.model.Parameter;
import org.apache.cxf.jaxrs.model.ParameterType;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.model.URITemplate;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.utils.InjectionUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.service.invoker.AbstractInvoker;

public class JAXRSInvoker extends AbstractInvoker {
    private static final Logger LOG = LogUtils.getL7dLogger(JAXRSInvoker.class);
    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(JAXRSInvoker.class);
    private static final String SERVICE_LOADER_AS_CONTEXT = "org.apache.cxf.serviceloader-context";
    private static final String SERVICE_OBJECT_SCOPE = "org.apache.cxf.service.scope";
    private static final String REQUEST_SCOPE = "request";    
    private static final String LAST_SERVICE_OBJECT = "org.apache.cxf.service.object.last";
    private static final String REQUEST_WAS_SUSPENDED = "org.apache.cxf.service.request.suspended";
    private static final String PROXY_INVOCATION_ERROR_FRAGMENT 
        = "object is not an instance of declaring class"; 
    
    public JAXRSInvoker() {
    }

    public Object invoke(Exchange exchange, Object request) {
        Response response = exchange.get(Response.class);
        if (response == null) {
            AsyncResponse asyncResp = exchange.get(AsyncResponse.class);
            if (asyncResp != null) {
                AsyncResponseImpl asyncImpl = (AsyncResponseImpl)asyncResp;
                asyncImpl.prepareContinuation();
                asyncImpl.handleTimeout();
                return handleAsyncResponse(exchange, asyncImpl.getResponseObject());
            }
        }
        if (response != null) {
            return new MessageContentsList(response);
        }
        
        
        
        ResourceProvider provider = getResourceProvider(exchange);
        Object rootInstance = getServiceObject(exchange);
        Object serviceObject = getActualServiceObject(exchange, rootInstance);
        
        try {
            return invoke(exchange, request, serviceObject);
        } finally {
            boolean suspended = exchange.getInMessage().getInterceptorChain().getState() == State.SUSPENDED;
            if (!suspended) {
                if (exchange.isOneWay()) {
                    ProviderFactory.getInstance(exchange.getInMessage()).clearThreadLocalProxies();
                }
                if (!isServiceObjectRequestScope(exchange.getInMessage())) {
                    provider.releaseInstance(exchange.getInMessage(), rootInstance);
                } else {
                    persistRoots(exchange, rootInstance, provider);
                }
            } else {
                persistRoots(exchange, rootInstance, provider);
                exchange.put(REQUEST_WAS_SUSPENDED, true);
            }
        }
    }

    private Object handleAsyncResponse(Exchange exchange, Object asyncObj) {
        if (asyncObj instanceof Throwable) {
            return handleFault(new Fault((Throwable)asyncObj), 
                               exchange.getInMessage(), null, null);    
        } else {
            return new MessageContentsList(asyncObj);
        }
    }
    
    private void persistRoots(Exchange exchange, Object rootInstance, Object provider) {
        exchange.put(JAXRSUtils.ROOT_INSTANCE, rootInstance);
        exchange.put(JAXRSUtils.ROOT_PROVIDER, provider);
    }
    
    @SuppressWarnings("unchecked")
    public Object invoke(Exchange exchange, Object request, Object resourceObject) {

        final OperationResourceInfo ori = exchange.get(OperationResourceInfo.class);
        final ClassResourceInfo cri = ori.getClassResourceInfo();
        final Message inMessage = exchange.getInMessage();
        final ProviderFactory providerFactory = ProviderFactory.getInstance(inMessage);

        boolean wasSuspended = exchange.remove(REQUEST_WAS_SUSPENDED) != null;
        
        if (!wasSuspended) {
            
            // Global and name-bound request filters
            if (!ori.isSubResourceLocator() && JAXRSUtils.runContainerRequestFilters(
                                                  providerFactory,
                                                  exchange.getInMessage(),
                                                  false, ori.getNameBindings())) {
                return new MessageContentsList(exchange.get(Response.class));
            }
            
            pushOntoStack(ori, ClassHelper.getRealClass(resourceObject), inMessage);
                    
            if (cri.isRoot()) {
                Object realResourceObject = ClassHelper.getRealObject(resourceObject);
                JAXRSUtils.injectParameters(ori, realResourceObject, inMessage);
    
                InjectionUtils.injectContexts(realResourceObject,
                                              ori.getClassResourceInfo(),
                                              inMessage);
                
                ProviderInfo<?> appProvider = 
                    (ProviderInfo<?>)exchange.getEndpoint().get(Application.class.getName());
                if (appProvider != null) {
                    InjectionUtils.injectContexts(appProvider.getProvider(),
                                                  appProvider,
                                                  inMessage);
                }
                
            }
        }
        

        Method methodToInvoke = InjectionUtils.checkProxy(
            cri.getMethodDispatcher().getMethod(ori), resourceObject);
        
        List<Object> params = null;
        if (request instanceof List) {
            params = CastUtils.cast((List<?>)request);
        } else if (request != null) {
            params = new MessageContentsList(request);
        }

        Object result = null;
        ClassLoaderHolder contextLoader = null;
        try {
            if (setServiceLoaderAsContextLoader(inMessage)) {
                contextLoader = ClassLoaderUtils
                    .setThreadContextClassloader(resourceObject.getClass().getClassLoader());
            }
            AsyncResponseImpl asyncResponse = null;
            if (!ori.isSubResourceLocator()) {
                asyncResponse = (AsyncResponseImpl)inMessage.get(AsyncResponse.class);
            }
            result = invoke(exchange, resourceObject, methodToInvoke, params);
            if (asyncResponse != null) {
                if (!asyncResponse.isSuspended() && !asyncResponse.isResumedByApplication()) {
                    asyncResponse.suspendContinuation();
                } else {
                    result = handleAsyncResponse(exchange, asyncResponse.getResponseObject());
                }
            }
        } catch (Fault ex) {
            return handleFault(ex, inMessage, cri, methodToInvoke);
        } finally {
            exchange.put(LAST_SERVICE_OBJECT, resourceObject);
            if (contextLoader != null) {
                contextLoader.reset();
            }
        }
        ClassResourceInfo subCri = null;
        if (ori.isSubResourceLocator()) {
            try {
                Message msg = exchange.getInMessage();
                MultivaluedMap<String, String> values = getTemplateValues(msg);
                String subResourcePath = values.getFirst(URITemplate.FINAL_MATCH_GROUP);
                String httpMethod = (String)msg.get(Message.HTTP_REQUEST_METHOD);
                String contentType = (String)msg.get(Message.CONTENT_TYPE);
                if (contentType == null) {
                    contentType = "*/*";
                }
                List<MediaType> acceptContentType =
                    (List<MediaType>)msg.getExchange().get(Message.ACCEPT_CONTENT_TYPE);

                result = checkResultObject(result, subResourcePath);

                subCri = cri.getSubResource(methodToInvoke.getReturnType(),
                    ClassHelper.getRealClass(result));
                if (subCri == null) {
                    org.apache.cxf.common.i18n.Message errorM =
                        new org.apache.cxf.common.i18n.Message("NO_SUBRESOURCE_FOUND",
                                                               BUNDLE,
                                                               subResourcePath);
                    LOG.severe(errorM.toString());
                    throw new NotFoundException();
                }

                OperationResourceInfo subOri = JAXRSUtils.findTargetMethod(subCri,
                                                         exchange.getInMessage(),
                                                         httpMethod,
                                                         values,
                                                         contentType,
                                                         acceptContentType,
                                                         true);
                exchange.put(OperationResourceInfo.class, subOri);
                msg.put(URITemplate.TEMPLATE_PARAMETERS, values);
                // work out request parameters for the sub-resource class. Here we
                // presume InputStream has not been consumed yet by the root resource class.
                List<Object> newParams = JAXRSUtils.processParameters(subOri, values, msg);
                msg.setContent(List.class, newParams);

                return this.invoke(exchange, newParams, result);
            } catch (IOException ex) {
                Response resp = JAXRSUtils.convertFaultToResponse(ex, exchange.getInMessage());
                if (resp == null) {
                    resp = JAXRSUtils.convertFaultToResponse(ex, exchange.getInMessage());
                }
                return new MessageContentsList(resp);
            } catch (WebApplicationException ex) {
                Response excResponse;
                if (JAXRSUtils.noResourceMethodForOptions(ex.getResponse(), 
                        (String)exchange.getInMessage().get(Message.HTTP_REQUEST_METHOD))) {
                    excResponse = JAXRSUtils.createResponse(subCri, null, null, 200, true);
                } else {
                    excResponse = JAXRSUtils.convertFaultToResponse(ex, exchange.getInMessage());
                }
                return new MessageContentsList(excResponse);
            }
        }

        return result;
    }
    
    private Object handleFault(Fault ex, Message inMessage, 
                               ClassResourceInfo cri, Method methodToInvoke) {
        String errorMessage = ex.getCause().getMessage();
        if (errorMessage != null && cri != null 
            && errorMessage.contains(PROXY_INVOCATION_ERROR_FRAGMENT)) {
            org.apache.cxf.common.i18n.Message errorM =
                new org.apache.cxf.common.i18n.Message("PROXY_INVOCATION_FAILURE",
                                                       BUNDLE,
                                                       methodToInvoke,
                                                       cri.getServiceClass().getName());
            LOG.severe(errorM.toString());
        }
        Response excResponse = JAXRSUtils.convertFaultToResponse(ex.getCause(), inMessage);
        if (excResponse == null) {
            ProviderFactory.getInstance(inMessage).clearThreadLocalProxies();
            ClassResourceInfo criRoot =
                (ClassResourceInfo)inMessage.getExchange().get(JAXRSUtils.ROOT_RESOURCE_CLASS);
            if (criRoot != null) {
                criRoot.clearThreadLocalProxies();
            }
            inMessage.getExchange().put(Message.PROPOGATE_EXCEPTION, 
                                        JAXRSUtils.propogateException(inMessage));
            throw ex;
        }
        return new MessageContentsList(excResponse);
    }

    @SuppressWarnings("unchecked")
    protected MultivaluedMap<String, String> getTemplateValues(Message msg) {
        MultivaluedMap<String, String> values = new MetadataMap<String, String>();
        MultivaluedMap<String, String> oldValues = 
            (MultivaluedMap<String, String>)msg.get(URITemplate.TEMPLATE_PARAMETERS);
        if (oldValues != null) {
            values.putAll(oldValues);
        }
        return values;
    }
    
    private boolean setServiceLoaderAsContextLoader(Message inMessage) {
        Object en = inMessage.getContextualProperty(SERVICE_LOADER_AS_CONTEXT);
        return Boolean.TRUE.equals(en) || "true".equals(en);
    }
    
    private boolean isServiceObjectRequestScope(Message inMessage) {
        Object scope = inMessage.getContextualProperty(SERVICE_OBJECT_SCOPE);
        return REQUEST_SCOPE.equals(scope);
    }
    
    private ResourceProvider getResourceProvider(Exchange exchange) {
        Object provider = exchange.remove(JAXRSUtils.ROOT_PROVIDER);
        if (provider == null) {
            OperationResourceInfo ori = exchange.get(OperationResourceInfo.class);
            ClassResourceInfo cri = ori.getClassResourceInfo();
            return cri.getResourceProvider();
        } else {
            return (ResourceProvider)provider;
        }
    }
    
    public Object getServiceObject(Exchange exchange) {
        
        Object root = exchange.remove(JAXRSUtils.ROOT_INSTANCE);
        if (root != null) {
            return root;
        }
        
        OperationResourceInfo ori = exchange.get(OperationResourceInfo.class);
        ClassResourceInfo cri = ori.getClassResourceInfo();

        return cri.getResourceProvider().getInstance(exchange.getInMessage());
    }
    
    protected Object getActualServiceObject(Exchange exchange, Object rootInstance) {
        
        Object last = exchange.get(LAST_SERVICE_OBJECT);
        return last !=  null ? last : rootInstance;
    }
    
    
    
    private static Object checkResultObject(Object result, String subResourcePath) {
        

        //the result becomes the object that will handle the request
        if (result != null) {
            if (result instanceof MessageContentsList) {
                result = ((MessageContentsList)result).get(0);
            } else if (result instanceof List) {
                result = ((List<?>)result).get(0);
            } else if (result.getClass().isArray()) {
                result = ((Object[])result)[0];
            }
        }
        if (result == null) {
            org.apache.cxf.common.i18n.Message errorM =
                new org.apache.cxf.common.i18n.Message("NULL_SUBRESOURCE",
                                                       BUNDLE,
                                                       subResourcePath);
            LOG.info(errorM.toString());
            throw new NotFoundException();
        }

        return result;
    }

    private void pushOntoStack(OperationResourceInfo ori, Class<?> realClass, Message msg) {
        OperationResourceInfoStack stack = msg.get(OperationResourceInfoStack.class);
        if (stack == null) {
            stack = new OperationResourceInfoStack();
            msg.put(OperationResourceInfoStack.class, stack);
        }
        
        
        @SuppressWarnings("unchecked")
        MultivaluedMap<String, String> params = 
            (MultivaluedMap<String, String>)msg.get(URITemplate.TEMPLATE_PARAMETERS);
        List<String> values = null;
        if (params == null || params.size() == 1) {
            values = Collections.emptyList();
        } else {
            values = new ArrayList<String>(params.size() - 1);
            for (Parameter pm : ori.getParameters()) {
                if (pm.getType() == ParameterType.PATH) {
                    List<String> paramValues = params.get(pm.getName());
                    if (paramValues != null) {
                        values.addAll(paramValues);
                    }
                    
                }
            }
        }
        stack.push(new MethodInvocationInfo(ori, realClass, values));
    }
}
