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
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.security.auth.callback.CallbackHandler;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.w3c.dom.Element;

import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.jaxrs.ext.MessageContextImpl;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.message.Message;
import org.apache.cxf.rs.security.saml.DeflateEncoderDecoder;
import org.apache.ws.security.WSPasswordCallback;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.CryptoType;
import org.apache.ws.security.saml.ext.OpenSAMLUtil;
import org.apache.ws.security.util.DOM2Writer;
import org.opensaml.common.SignableSAMLObject;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.security.x509.X509KeyInfoGeneratorFactory;
import org.opensaml.xml.signature.KeyInfo;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureConstants;

public class SamlPostBindingFilter extends AbstractServiceProviderFilter {
    
    private boolean useDeflateEncoding;
    
    public void setUseDeflateEncoding(boolean useDeflateEncoding) {
        this.useDeflateEncoding = useDeflateEncoding;
    }
    
    public Response handleRequest(Message m, ClassResourceInfo resourceClass) {
        if (checkSecurityContext(m)) {
            return null;
        } else {
            try {
                SamlRequestInfo info = createSamlRequestInfo(m);
                info.setIdpServiceAddress(getIdpServiceAddress());
                // This depends on RequestDispatcherProvider linking
                // SamlRequestInfo with the jsp page which will fill
                // in the XHTML form using SamlRequestInfo
                // in principle we could've built the XHTML form right here
                // but it will be cleaner to get that done in JSP
                
                String contextCookie = createCookie(SSOConstants.RELAY_STATE,
                                                    info.getRelayState(),
                                                    info.getWebAppContext(),
                                                    info.getWebAppDomain());
                new MessageContextImpl(m).getHttpServletResponse().addHeader(
                    HttpHeaders.SET_COOKIE, contextCookie);
                
                return Response.ok(info)
                               .type("text/html")
                               .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
                               .header("Pragma", "no-cache") 
                               .build();
                
            } catch (Exception ex) {
                throw new InternalServerErrorException(ex);
            }
        }
    }
    
    protected String encodeAuthnRequest(Element authnRequest) throws IOException {
        String requestMessage = DOM2Writer.nodeToString(authnRequest);
        
        byte[] deflatedBytes = null;
        // Not correct according to the spec but required by some IDPs.
        if (useDeflateEncoding) {
            DeflateEncoderDecoder encoder = new DeflateEncoderDecoder();
            deflatedBytes = encoder.deflateToken(requestMessage.getBytes("UTF-8"));
        } else {
            deflatedBytes = requestMessage.getBytes("UTF-8");
        }

        return Base64Utility.encode(deflatedBytes);
    }
    
    protected void signAuthnRequest(AuthnRequest authnRequest) throws Exception {
        Crypto crypto = getSignatureCrypto();
        if (crypto == null) {
            LOG.fine("No crypto instance of properties file configured for signature");
            throw new InternalServerErrorException();
        }
        String signatureUser = getSignatureUsername();
        if (signatureUser == null) {
            LOG.fine("No user configured for signature");
            throw new InternalServerErrorException();
        }
        CallbackHandler callbackHandler = getCallbackHandler();
        if (callbackHandler == null) {
            LOG.fine("No CallbackHandler configured to supply a password for signature");
            throw new InternalServerErrorException();
        }
        
        CryptoType cryptoType = new CryptoType(CryptoType.TYPE.ALIAS);
        cryptoType.setAlias(signatureUser);
        X509Certificate[] issuerCerts = crypto.getX509Certificates(cryptoType);
        if (issuerCerts == null) {
            throw new WSSecurityException(
                "No issuer certs were found to sign the request using name: " + signatureUser
            );
        }

        String sigAlgo = SSOConstants.RSA_SHA1;
        String pubKeyAlgo = issuerCerts[0].getPublicKey().getAlgorithm();
        LOG.fine("automatic sig algo detection: " + pubKeyAlgo);
        if (pubKeyAlgo.equalsIgnoreCase("DSA")) {
            sigAlgo = SSOConstants.DSA_SHA1;
        }
        LOG.fine("Using Signature algorithm " + sigAlgo);
        
        // Get the password
        WSPasswordCallback[] cb = {new WSPasswordCallback(signatureUser, WSPasswordCallback.SIGNATURE)};
        callbackHandler.handle(cb);
        String password = cb[0].getPassword();
        
        // Get the private key
        PrivateKey privateKey = null;
        try {
            privateKey = crypto.getPrivateKey(signatureUser, password);
        } catch (Exception ex) {
            throw new WSSecurityException(ex.getMessage(), ex);
        }
        
        // Create the signature
        Signature signature = OpenSAMLUtil.buildSignature();
        signature.setCanonicalizationAlgorithm(SignatureConstants.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        signature.setSignatureAlgorithm(sigAlgo);
        
        BasicX509Credential signingCredential = new BasicX509Credential();
        signingCredential.setEntityCertificate(issuerCerts[0]);
        signingCredential.setPrivateKey(privateKey);

        signature.setSigningCredential(signingCredential);

        X509KeyInfoGeneratorFactory kiFactory = new X509KeyInfoGeneratorFactory();
        kiFactory.setEmitEntityCertificate(true);
        
        try {
            KeyInfo keyInfo = kiFactory.newInstance().generate(signingCredential);
            signature.setKeyInfo(keyInfo);
        } catch (org.opensaml.xml.security.SecurityException ex) {
            throw new WSSecurityException(
                    "Error generating KeyInfo from signing credential", ex);
        }
        
        SignableSAMLObject signableObject = (SignableSAMLObject) authnRequest;
        signableObject.setSignature(signature);
        signableObject.releaseDOM();
        signableObject.releaseChildrenDOM(true);
        
    }
    
}
