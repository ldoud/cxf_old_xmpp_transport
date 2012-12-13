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
package org.apache.cxf.ws.security.policy.builders;

import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.ws.policy.PolicyBuilder;
import org.apache.cxf.ws.policy.PolicyConstants;
import org.apache.cxf.ws.security.policy.SP11Constants;
import org.apache.cxf.ws.security.policy.SP12Constants;
import org.apache.cxf.ws.security.policy.SPConstants;
import org.apache.cxf.ws.security.policy.model.HttpsToken;
import org.apache.neethi.Assertion;
import org.apache.neethi.AssertionBuilderFactory;
import org.apache.neethi.builders.AssertionBuilder;


/**
 * This is a standard assertion builder implementation for the https token 
 * as specified by the ws security policy 1.2 specification. In order for this builder to be used
 * it is required that the security policy namespace uri is {@link SP12Constants#SP_NS} 
 * The builder will handle
 * <ul>
 *  <li><code>HttpBasicAuthentication</code></li>
 *  <li><code>HttpDigestAuthentication</code></li>
 *  <li><code>RequireClientCertificate</code></li>
 * </ul> 
 * alternatives in the HttpsToken considering both cases whether the policy is normalized or not.
 * 
 */
public class HttpsTokenBuilder implements AssertionBuilder<Element> {
    
    private static final Logger LOG = LogUtils.getL7dLogger(HttpsTokenBuilder.class);
    
    PolicyBuilder builder;
    public HttpsTokenBuilder(PolicyBuilder b) {
        builder = b;
    }
    
    /**
     * {@inheritDoc}
     */
    public Assertion build(Element element, AssertionBuilderFactory factory) {
        SPConstants consts = SP11Constants.SP_NS.equals(element.getNamespaceURI())
            ? SP11Constants.INSTANCE : SP12Constants.INSTANCE;

        
        HttpsToken httpsToken = new HttpsToken(consts);
        httpsToken.setOptional(PolicyConstants.isOptional(element));
        httpsToken.setIgnorable(PolicyConstants.isIgnorable(element));

        if (consts.getVersion() == SPConstants.Version.SP_V11) {
            String attr = DOMUtils.getAttribute(element, SPConstants.REQUIRE_CLIENT_CERTIFICATE);
            if (attr != null) {
                httpsToken.setRequireClientCertificate("true".equals(attr));
            }
        } else {
            Element polEl = PolicyConstants.findPolicyElement(element);
            if (polEl == null) {
                LOG.warning("sp:HttpsToken/wsp:Policy should have a value!");
            } else {
                Element child = DOMUtils.getFirstElement(polEl);
                if (child != null) {
                    if (SP12Constants.HTTP_BASIC_AUTHENTICATION.equals(DOMUtils.getElementQName(child))) {
                        httpsToken.setHttpBasicAuthentication(true);
                    } else if (SP12Constants.HTTP_DIGEST_AUTHENTICATION
                            .equals(DOMUtils.getElementQName(child))) {
                        httpsToken.setHttpDigestAuthentication(true);
                    } else if (SP12Constants.REQUIRE_CLIENT_CERTIFICATE
                            .equals(DOMUtils.getElementQName(child))) {
                        httpsToken.setRequireClientCertificate(true);
                    }
                }
            }
        }

        return httpsToken;
    }

    /**
     * {@inheritDoc}
     */
    public QName[] getKnownElements() {
        return new QName[]{SP11Constants.HTTPS_TOKEN, SP12Constants.HTTPS_TOKEN};
    }
    
}
