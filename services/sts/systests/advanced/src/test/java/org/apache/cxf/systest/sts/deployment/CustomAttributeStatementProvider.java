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
package org.apache.cxf.systest.sts.deployment;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.cxf.sts.claims.Claim;
import org.apache.cxf.sts.claims.ClaimCollection;
import org.apache.cxf.sts.claims.ClaimsManager;
import org.apache.cxf.sts.claims.ClaimsParameters;
import org.apache.cxf.sts.token.provider.AttributeStatementProvider;
import org.apache.cxf.sts.token.provider.TokenProviderParameters;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.saml.ext.bean.AttributeBean;
import org.apache.ws.security.saml.ext.bean.AttributeStatementBean;
import org.apache.ws.security.saml.ext.builder.SAML2Constants;

public class CustomAttributeStatementProvider implements AttributeStatementProvider {
    
    private String nameFormat = SAML2Constants.ATTRNAME_FORMAT_UNSPECIFIED;

    public AttributeStatementBean getStatement(TokenProviderParameters providerParameters) {

        // Handle Claims
        ClaimsManager claimsManager = providerParameters.getClaimsManager();
        ClaimCollection retrievedClaims = new ClaimCollection();
        if (claimsManager != null) {
            ClaimsParameters params = new ClaimsParameters();
            params.setAdditionalProperties(providerParameters.getAdditionalProperties());
            params.setAppliesToAddress(providerParameters.getAppliesToAddress());
            params.setEncryptionProperties(providerParameters.getEncryptionProperties());
            params.setKeyRequirements(providerParameters.getKeyRequirements());
            params.setPrincipal(providerParameters.getPrincipal());
            params.setRealm(providerParameters.getRealm());
            params.setStsProperties(providerParameters.getStsProperties());
            params.setTokenRequirements(providerParameters.getTokenRequirements());
            params.setTokenStore(providerParameters.getTokenStore());
            params.setWebServiceContext(providerParameters.getWebServiceContext());
            retrievedClaims = 
                claimsManager.retrieveClaimValues(
                    providerParameters.getRequestedPrimaryClaims(),
                    providerParameters.getRequestedSecondaryClaims(),
                    params
                );
        }
        if (retrievedClaims == null) {
            return null;
        }
        
        Iterator<Claim> claimIterator = retrievedClaims.iterator();
        if (!claimIterator.hasNext()) {
            return null;
        }

        List<AttributeBean> attributeList = new ArrayList<AttributeBean>();
        String tokenType = providerParameters.getTokenRequirements().getTokenType();

        AttributeStatementBean attrBean = new AttributeStatementBean();
        while (claimIterator.hasNext()) {
            Claim claim = claimIterator.next();
            AttributeBean attributeBean = new AttributeBean();
            
            URI claimType = claim.getClaimType();
            if (WSConstants.WSS_SAML2_TOKEN_TYPE.equals(tokenType)
                || WSConstants.SAML2_NS.equals(tokenType)) {
                attributeBean.setQualifiedName(claimType.toString());
                attributeBean.setNameFormat(nameFormat);
            } else {
                String uri = claimType.toString();
                int lastSlash = uri.lastIndexOf("/");
                if (lastSlash == (uri.length() - 1)) {
                    uri = uri.substring(0, lastSlash);
                    lastSlash = uri.lastIndexOf("/");
                }

                String namespace = uri.substring(0, lastSlash);
                String name = uri.substring(lastSlash + 1, uri.length());
                
                attributeBean.setSimpleName(name);
                attributeBean.setQualifiedName(namespace);
            }
            attributeBean.setAttributeValues(claim.getValues());
            attributeList.add(attributeBean);
        }
        attrBean.setSamlAttributes(attributeList);

        return attrBean;
    }
    
    public String getNameFormat() {
        return nameFormat;
    }

    public void setNameFormat(String nameFormat) {
        this.nameFormat = nameFormat;
    }

}
