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
import java.net.URLEncoder;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;

import javax.security.auth.callback.CallbackHandler;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.w3c.dom.Element;

import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.message.Message;
import org.apache.cxf.rs.security.saml.DeflateEncoderDecoder;
import org.apache.ws.security.WSPasswordCallback;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.CryptoType;
import org.apache.ws.security.util.Base64;
import org.apache.ws.security.util.DOM2Writer;
import org.opensaml.saml2.core.AuthnRequest;

public class SamlRedirectBindingFilter extends AbstractServiceProviderFilter {
    
    public Response handleRequest(Message m, ClassResourceInfo resourceClass) {
        if (checkSecurityContext(m)) {
            return null;
        } else {
            try {
                SamlRequestInfo info = createSamlRequestInfo(m);
                String urlEncodedRequest = 
                    URLEncoder.encode(info.getSamlRequest(), "UTF-8");
                
                UriBuilder ub = UriBuilder.fromUri(getIdpServiceAddress());
                
                ub.queryParam(SSOConstants.SAML_REQUEST, urlEncodedRequest);
                ub.queryParam(SSOConstants.RELAY_STATE, info.getRelayState());
                if (isSignRequest()) {
                    signRequest(urlEncodedRequest, info.getRelayState(), ub);
                }
                
                String contextCookie = createCookie(SSOConstants.RELAY_STATE,
                                                    info.getRelayState(),
                                                    info.getWebAppContext(),
                                                    info.getWebAppDomain());
                
                return Response.seeOther(ub.build())
                               .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
                               .header("Pragma", "no-cache") 
                               .header(HttpHeaders.SET_COOKIE, contextCookie)
                               .build();
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new InternalServerErrorException(ex);
            }
        }
    }
    
    protected void signAuthnRequest(AuthnRequest authnRequest) throws Exception {
        // Do nothing as we sign the request in a different way for the redirect binding
    }
    
    protected String encodeAuthnRequest(Element authnRequest) throws IOException {
        String requestMessage = DOM2Writer.nodeToString(authnRequest);

        DeflateEncoderDecoder encoder = new DeflateEncoderDecoder();
        byte[] deflatedBytes = encoder.deflateToken(requestMessage.getBytes("UTF-8"));

        return Base64Utility.encode(deflatedBytes);
    }
    
    /**
     * Sign a request according to the redirect binding spec for Web SSO
     */
    private void signRequest(
        String authnRequest,
        String relayState,
        UriBuilder ub
    ) throws Exception {
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
        String jceSigAlgo = "SHA1withRSA";
        LOG.fine("automatic sig algo detection: " + pubKeyAlgo);
        if (pubKeyAlgo.equalsIgnoreCase("DSA")) {
            sigAlgo = SSOConstants.DSA_SHA1;
            jceSigAlgo = "SHA1withDSA";
        }
        LOG.fine("Using Signature algorithm " + sigAlgo);
        ub.queryParam(SSOConstants.SIG_ALG, URLEncoder.encode(sigAlgo, "UTF-8"));
        
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
        
        // Sign the request
        Signature signature = Signature.getInstance(jceSigAlgo);
        signature.initSign(privateKey);
       
        String requestToSign = 
            SSOConstants.SAML_REQUEST + "=" + authnRequest + "&"
            + SSOConstants.RELAY_STATE + "=" + relayState + "&"
            + SSOConstants.SIG_ALG + "=" + URLEncoder.encode(sigAlgo, "UTF-8");

        signature.update(requestToSign.getBytes("UTF-8"));
        byte[] signBytes = signature.sign();
        
        String encodedSignature = Base64.encode(signBytes);
        
        ub.queryParam(SSOConstants.SIGNATURE, URLEncoder.encode(encodedSignature, "UTF-8"));
        
    }
    
}
