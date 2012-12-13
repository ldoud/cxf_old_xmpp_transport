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

package org.apache.cxf.jaxrs.interceptor;

import java.io.IOException;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.jaxrs.JAXRSServiceImpl;
import org.apache.cxf.jaxrs.ext.RequestHandler;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.impl.RequestPreprocessor;
import org.apache.cxf.jaxrs.impl.UriInfoImpl;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.model.ProviderInfo;
import org.apache.cxf.jaxrs.model.URITemplate;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.utils.HttpUtils;
import org.apache.cxf.jaxrs.utils.InjectionUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.Service;

public class JAXRSInInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final Logger LOG = LogUtils.getL7dLogger(JAXRSInInterceptor.class);
    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(JAXRSInInterceptor.class);
    
    public JAXRSInInterceptor() {
        super(Phase.UNMARSHAL);
    }

    @Override
    public void handleFault(Message message) {
        super.handleFault(message);
        
        LOG.fine("Cleanup thread local variables");
        
        Object rootInstance = message.getExchange().remove(JAXRSUtils.ROOT_INSTANCE);
        Object rootProvider = message.getExchange().remove(JAXRSUtils.ROOT_PROVIDER);
        if (rootInstance != null && rootProvider != null) {
            try {
                ((ResourceProvider)rootProvider).releaseInstance(message, rootInstance);
            } catch (Throwable tex) {
                LOG.warning("Exception occurred during releasing the service instance, " + tex.getMessage());
            }
        }
        ProviderFactory.getInstance(message).clearThreadLocalProxies();
        ClassResourceInfo cri = (ClassResourceInfo)message.getExchange().get(JAXRSUtils.ROOT_RESOURCE_CLASS);
        if (cri != null) {
            cri.clearThreadLocalProxies();
        }
    }
    
    public void handleMessage(Message message) {
        
        if (message.getExchange().get(OperationResourceInfo.class) != null) {
            // it's a suspended invocation;
            return;
        }
        
        try {
            processRequest(message);
        } catch (RuntimeException ex) {
            Response excResponse = JAXRSUtils.convertFaultToResponse(ex, message);
            if (excResponse == null) {
                ProviderFactory.getInstance(message).clearThreadLocalProxies();
                message.getExchange().put(Message.PROPOGATE_EXCEPTION, 
                                          JAXRSUtils.propogateException(message));
                throw ex;
            }
            message.getExchange().put(Response.class, excResponse);
        }
        
        
    }
    
    private void processRequest(Message message) {
        
        ProviderFactory providerFactory = ProviderFactory.getInstance(message);
        
        RequestPreprocessor rp = providerFactory.getRequestPreprocessor();
        if (rp != null) {
            rp.preprocess(message, new UriInfoImpl(message, null));
            if (message.getExchange().get(Response.class) != null) {
                return;
            }
        }
        
        // Global pre-match request filters
        if (JAXRSUtils.runContainerRequestFilters(providerFactory, message, true, null)) {
            return;
        }
        
        String requestContentType = (String)message.get(Message.CONTENT_TYPE);
        if (requestContentType == null) {
            requestContentType = "*/*";
        }
        
        String rawPath = HttpUtils.getPathToMatch(message, true);
        
        //1. Matching target resource class
        Service service = message.getExchange().get(Service.class);
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)service).getClassResourceInfos();

        String acceptTypes = HttpUtils.getProtocolHeader(message, Message.ACCEPT_CONTENT_TYPE, null);
        if (acceptTypes == null) {
            acceptTypes = "*/*";
            message.put(Message.ACCEPT_CONTENT_TYPE, acceptTypes);
        }
        List<MediaType> acceptContentTypes = null;
        try {
            acceptContentTypes = JAXRSUtils.sortMediaTypes(acceptTypes);
        } catch (IllegalArgumentException ex) {
            throw new NotAcceptableException();
        }
        message.getExchange().put(Message.ACCEPT_CONTENT_TYPE, acceptContentTypes);

        MultivaluedMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, 
                                          rawPath, 
                                          values,
                                          message);
        if (resource == null) {
            org.apache.cxf.common.i18n.Message errorMsg = 
                new org.apache.cxf.common.i18n.Message("NO_ROOT_EXC", 
                                                   BUNDLE,
                                                   message.get(Message.REQUEST_URI),
                                                   rawPath);
            LOG.warning(errorMsg.toString());
            Response resp = JAXRSUtils.createResponse(null, message, errorMsg.toString(), 
                    Response.Status.NOT_FOUND.getStatusCode(), false);
            throw new NotFoundException(resp);
        }

        message.getExchange().put(JAXRSUtils.ROOT_RESOURCE_CLASS, resource);

        String httpMethod = HttpUtils.getProtocolHeader(message, Message.HTTP_REQUEST_METHOD, "POST");
        OperationResourceInfo ori = null;     
        
        boolean operChecked = false;
        List<ProviderInfo<RequestHandler>> shs = providerFactory.getRequestHandlers();
        for (ProviderInfo<RequestHandler> sh : shs) {
            if (ori == null && !operChecked) {
                try {                
                    ori = JAXRSUtils.findTargetMethod(resource, 
                        message, httpMethod, values, 
                        requestContentType, acceptContentTypes, false);
                    setExchangeProperties(message, ori, values, resources.size());
                } catch (WebApplicationException ex) {
                    operChecked = true;
                }
                
            }
            InjectionUtils.injectContexts(sh.getProvider(), sh, message);
            Response response = sh.getProvider().handleRequest(message, resource);
            if (response != null) {
                message.getExchange().put(Response.class, response);
                return;
            }
            
        }
        
        if (ori == null) {
            try {                
                ori = JAXRSUtils.findTargetMethod(resource, message, 
                                            httpMethod, values, requestContentType, acceptContentTypes, true);
                setExchangeProperties(message, ori, values, resources.size());
            } catch (WebApplicationException ex) {
                if (JAXRSUtils.noResourceMethodForOptions(ex.getResponse(), httpMethod)) {
                    Response response = JAXRSUtils.createResponse(resource, null, null, 200, true);
                    message.getExchange().put(Response.class, response);
                    return;
                } else {
                    throw ex;
                }
            }
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Request path is: " + rawPath);
            LOG.fine("Request HTTP method is: " + httpMethod);
            LOG.fine("Request contentType is: " + requestContentType);
            LOG.fine("Accept contentType is: " + acceptTypes);

            LOG.fine("Found operation: " + ori.getMethodToInvoke().getName());
        }
        
        setExchangeProperties(message, ori, values, resources.size());
        
        //Process parameters
        try {
            List<Object> params = JAXRSUtils.processParameters(ori, values, message);
            message.setContent(List.class, params);
        } catch (IOException ex) {
            Response excResponse = JAXRSUtils.convertFaultToResponse(ex, message);
            if (excResponse == null) {
                throw new InternalServerErrorException(ex);
            } else {
                message.getExchange().put(Response.class, excResponse);
            }
        }
        
    }
    
    private void setExchangeProperties(Message message, OperationResourceInfo ori, 
                                      MultivaluedMap<String, String> values,
                                      int numberOfResources) {
        message.getExchange().put(OperationResourceInfo.class, ori);
        message.put("org.apache.cxf.resource.method", ori.getMethodToInvoke());
        message.put(URITemplate.TEMPLATE_PARAMETERS, values);
        
        String plainOperationName = ori.getMethodToInvoke().getName();
        if (numberOfResources > 1) {
            plainOperationName = ori.getClassResourceInfo().getServiceClass().getSimpleName()
                + "#" + plainOperationName;
        }
        message.getExchange().put("org.apache.cxf.resource.operation.name", plainOperationName);
        
        boolean oneway = ori.isOneway() 
            || MessageUtils.isTrue(HttpUtils.getProtocolHeader(message, Message.ONE_WAY_REQUEST, null));
        message.getExchange().setOneWay(oneway);
    }
}
