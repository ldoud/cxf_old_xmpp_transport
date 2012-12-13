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
package org.apache.cxf.rs.security.saml.sso;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.security.Principal;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PreDestroy;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.security.SimplePrincipal;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.jaxrs.ext.RequestHandler;
import org.apache.cxf.jaxrs.impl.HttpHeadersImpl;
import org.apache.cxf.jaxrs.impl.UriInfoImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.rs.security.saml.SAMLUtils;
import org.apache.cxf.rs.security.saml.assertion.Subject;
import org.apache.cxf.rs.security.saml.sso.state.RequestState;
import org.apache.cxf.rs.security.saml.sso.state.ResponseState;
import org.apache.cxf.security.SecurityContext;
import org.apache.ws.security.saml.ext.AssertionWrapper;
import org.apache.ws.security.saml.ext.OpenSAMLUtil;
import org.opensaml.saml2.core.AuthnRequest;

public abstract class AbstractServiceProviderFilter extends AbstractSSOSpHandler 
    implements RequestHandler {
    
    protected static final Logger LOG = 
        LogUtils.getL7dLogger(AbstractServiceProviderFilter.class);
    protected static final ResourceBundle BUNDLE = 
        BundleUtils.getBundle(AbstractServiceProviderFilter.class);
    
    private String idpServiceAddress;
    private String issuerId;
    private String assertionConsumerServiceAddress;
    private AuthnRequestBuilder authnRequestBuilder = new DefaultAuthnRequestBuilder();
    private boolean signRequest;
    private String signatureUsername;
    
    private String webAppDomain;
    private boolean addWebAppContext = true;
    private boolean addEndpointAddressToContext;
    
    public void setAddEndpointAddressToContext(boolean add) {
        addEndpointAddressToContext = add;
    }
    
    public void setSignRequest(boolean signRequest) {
        this.signRequest = signRequest;
    }
    
    public boolean isSignRequest() {
        return signRequest;
    }
    
    public void setAuthnRequestBuilder(AuthnRequestBuilder authnRequestBuilder) {
        this.authnRequestBuilder = authnRequestBuilder;
    }
    
    public void setAssertionConsumerServiceAddress(
            String assertionConsumerServiceAddress) {
        this.assertionConsumerServiceAddress = assertionConsumerServiceAddress;
    }

    public void setIssuerId(String issuerId) {
        this.issuerId = issuerId;
    }
    
    public void setIdpServiceAddress(String idpServiceAddress) {
        this.idpServiceAddress = idpServiceAddress;
    }

    public String getIdpServiceAddress() {
        return idpServiceAddress;
    }
    
    /**
     * Set the username/alias to use to sign any request
     * @param signatureUsername the username/alias to use to sign any request
     */
    public void setSignatureUsername(String signatureUsername) {
        this.signatureUsername = signatureUsername;
        LOG.fine("Setting signatureUsername: " + signatureUsername);
    }
    
    /**
     * Get the username/alias to use to sign any request
     * @return the username/alias to use to sign any request
     */
    public String getSignatureUsername() {
        return signatureUsername;
    }
    
    @PreDestroy
    public void close() throws IOException {
        if (getStateProvider() != null) {
            getStateProvider().close();
        }
    }
    
    private String getIssuerId(Message m) {
        if (issuerId == null) {
            return new UriInfoImpl(m).getBaseUri().toString();
        } else {
            return issuerId;
        }
    }
    
    protected boolean checkSecurityContext(Message m) {
        HttpHeaders headers = new HttpHeadersImpl(m);
        Map<String, Cookie> cookies = headers.getCookies();
        
        Cookie securityContextCookie = cookies.get(SSOConstants.SECURITY_CONTEXT_TOKEN);
        
        ResponseState responseState = getValidResponseState(securityContextCookie, m);
        if (responseState == null) {
            return false;    
        }
        
        Cookie relayStateCookie = cookies.get(SSOConstants.RELAY_STATE);
        if (relayStateCookie == null) {
            reportError("MISSING_RELAY_COOKIE");
            return false;
        }
        String originalRelayState = responseState.getRelayState();
        if (!originalRelayState.equals(relayStateCookie.getValue())) {
            // perhaps the response state should also be removed
            reportError("INVALID_RELAY_STATE");
            return false;
        }
        try {
            String assertion = responseState.getAssertion();
            AssertionWrapper assertionWrapper = 
                new AssertionWrapper(
                    DOMUtils.readXml(new StringReader(assertion)).getDocumentElement());
            setSecurityContext(m, assertionWrapper);
        } catch (Exception ex) {
            reportError("INVALID_RESPONSE_STATE");
            return false;
        }
        return true;
    }
    
    protected void setSecurityContext(Message m, AssertionWrapper assertionWrapper) {
        // don't worry about roles/claims for now, just set a basic SecurityContext
        Subject subject = SAMLUtils.getSubject(m, assertionWrapper);
        final String name = subject.getName();
        
        if (name != null) {
            final SecurityContext sc = new SecurityContext() {

                public Principal getUserPrincipal() {
                    return new SimplePrincipal(name);
                }

                public boolean isUserInRole(String role) {
                    return false;
                }
            };
            m.put(SecurityContext.class, sc);
        }
    }
    
    protected ResponseState getValidResponseState(Cookie securityContextCookie, 
                                                  Message m) {
        if (securityContextCookie == null) {
            // most likely it means that the user has not been offered
            // a chance to get logged on yet, though it might be that the browser
            // has removed an expired cookie from its cache; warning is too noisy in the
            // former case
            reportTrace("MISSING_RESPONSE_STATE");
            return null;
        }
        String contextKey = securityContextCookie.getValue();
        
        ResponseState responseState = getStateProvider().getResponseState(contextKey);
        
        if (responseState == null) {
            reportError("MISSING_RESPONSE_STATE");
            return null;
        }
        if (isStateExpired(responseState.getCreatedAt(), responseState.getExpiresAt())) {
            reportError("EXPIRED_RESPONSE_STATE");
            getStateProvider().removeResponseState(contextKey);
            return null;
        }
        String webAppContext = getWebAppContext(m);
        if (webAppDomain != null 
            && (responseState.getWebAppDomain() == null 
                || !webAppDomain.equals(responseState.getWebAppDomain()))
            || responseState.getWebAppContext() == null
            || !webAppContext.equals(responseState.getWebAppContext())) {
            getStateProvider().removeResponseState(contextKey);
            reportError("INVALID_RESPONSE_STATE");
            return null;
        }
        if (responseState.getAssertion() == null) {
            reportError("INVALID_RESPONSE_STATE");
            return null;
        }
        return responseState;
    }
    
    protected SamlRequestInfo createSamlRequestInfo(Message m) throws Exception {
        Document doc = DOMUtils.createDocument();
        doc.appendChild(doc.createElement("root"));
 
        // Create the AuthnRequest
        AuthnRequest authnRequest = 
            authnRequestBuilder.createAuthnRequest(
                m, getIssuerId(m), getAbsoluteAssertionServiceAddress(m)
            );
        if (isSignRequest()) {
            authnRequest.setDestination(idpServiceAddress);
            signAuthnRequest(authnRequest);
        }
        Element authnRequestElement = OpenSAMLUtil.toDom(authnRequest, doc);
        String authnRequestEncoded = encodeAuthnRequest(authnRequestElement);
        
        SamlRequestInfo info = new SamlRequestInfo();
        info.setSamlRequest(authnRequestEncoded);
        
        String webAppContext = getWebAppContext(m);
        String originalRequestURI = new UriInfoImpl(m).getRequestUri().toString();
        
        RequestState requestState = new RequestState(originalRequestURI,
                                                     getIdpServiceAddress(),
                                                     authnRequest.getID(),
                                                     getIssuerId(m),
                                                     webAppContext,
                                                     getWebAppDomain(),
                                                     System.currentTimeMillis());
        
        String relayState = URLEncoder.encode(UUID.randomUUID().toString(), "UTF-8");
        getStateProvider().setRequestState(relayState, requestState);
        info.setRelayState(relayState);
        info.setWebAppContext(webAppContext);
        info.setWebAppDomain(getWebAppDomain());
        
        return info;
    }
    
    protected abstract String encodeAuthnRequest(Element authnRequest) throws IOException;
    
    protected abstract void signAuthnRequest(AuthnRequest authnRequest) throws Exception;
    
    private String getAbsoluteAssertionServiceAddress(Message m) {
        if (assertionConsumerServiceAddress == null) {    
            //TODO: Review the possibility of using this filter
            //for validating SAMLResponse too
            reportError("MISSING_ASSERTION_SERVICE_URL");
            throw new InternalServerErrorException();
        }
        if (!assertionConsumerServiceAddress.startsWith("http")) {
            String httpBasePath = (String)m.get("http.base.path");
            return UriBuilder.fromUri(httpBasePath)
                             .path(assertionConsumerServiceAddress)
                             .build()
                             .toString();
        } else {
            return assertionConsumerServiceAddress;
        }
    }
    
    protected void reportError(String code) {
        org.apache.cxf.common.i18n.Message errorMsg = 
            new org.apache.cxf.common.i18n.Message(code, BUNDLE);
        LOG.warning(errorMsg.toString());
    }
    
    protected void reportTrace(String code) {
        if (LOG.isLoggable(Level.FINE)) {
            org.apache.cxf.common.i18n.Message errorMsg = 
                new org.apache.cxf.common.i18n.Message(code, BUNDLE);
            LOG.fine(errorMsg.toString());
        }
    }

    private String getWebAppContext(Message m) {
        if (addWebAppContext) {
            if (addEndpointAddressToContext) {
                return new UriInfoImpl(m).getBaseUri().getRawPath();
            } else {
                String httpBasePath = (String)m.get("http.base.path");
                return URI.create(httpBasePath).getRawPath();
            }
        } else {
            return "/";
        }
    }
    
    public String getWebAppDomain() {
        return webAppDomain;
    }

    public void setWebAppDomain(String webAppDomain) {
        this.webAppDomain = webAppDomain;
    }

    public void setAddWebAppContext(boolean addWebAppContext) {
        this.addWebAppContext = addWebAppContext;
    }
        
}
