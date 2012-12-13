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

import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;

import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.ws.policy.PolicyBuilder;
import org.apache.cxf.ws.security.policy.SP11Constants;
import org.apache.cxf.ws.security.policy.SPConstants;
import org.apache.cxf.ws.security.policy.SPConstants.SupportTokenType;
import org.apache.cxf.ws.security.policy.model.AlgorithmSuite;
import org.apache.cxf.ws.security.policy.model.SignedEncryptedElements;
import org.apache.cxf.ws.security.policy.model.SignedEncryptedParts;
import org.apache.cxf.ws.security.policy.model.SupportingToken;
import org.apache.cxf.ws.security.policy.model.Token;
import org.apache.neethi.Assertion;
import org.apache.neethi.AssertionBuilderFactory;
import org.apache.neethi.Policy;
import org.apache.neethi.builders.AssertionBuilder;


public class SupportingTokensBuilder implements AssertionBuilder<Element> {
    
    PolicyBuilder builder;
    public SupportingTokensBuilder(PolicyBuilder b) {
        builder = b;
    }
    public QName[] getKnownElements() {
        return new QName[]{SP11Constants.SUPPORTING_TOKENS, 
                           SP11Constants.SIGNED_SUPPORTING_TOKENS,
                           SP11Constants.ENDORSING_SUPPORTING_TOKENS,
                           SP11Constants.SIGNED_ENDORSING_SUPPORTING_TOKENS};
    }
    
    public Assertion build(Element element, AssertionBuilderFactory factory)
        throws IllegalArgumentException {
        QName name = DOMUtils.getElementQName(element);
        SupportingToken supportingToken = null;

        if (SP11Constants.SUPPORTING_TOKENS.equals(name)) {
            supportingToken = new SupportingToken(SupportTokenType.SUPPORTING_TOKEN_SUPPORTING,
                                                  SP11Constants.INSTANCE,
                                                  builder);
        } else if (SP11Constants.SIGNED_SUPPORTING_TOKENS.equals(name)) {
            supportingToken = new SupportingToken(SupportTokenType.SUPPORTING_TOKEN_SIGNED, 
                                                  SP11Constants.INSTANCE,
                                                  builder);
        } else if (SP11Constants.ENDORSING_SUPPORTING_TOKENS.equals(name)) {
            supportingToken = new SupportingToken(SupportTokenType.SUPPORTING_TOKEN_ENDORSING, 
                                                  SP11Constants.INSTANCE,
                                                  builder);
        } else if (SP11Constants.SIGNED_ENDORSING_SUPPORTING_TOKENS.equals(name)) {
            supportingToken = new SupportingToken(SupportTokenType.SUPPORTING_TOKEN_SIGNED_ENDORSING,
                                                  SP11Constants.INSTANCE,
                                                  builder);
        }

        Policy policy = builder.getPolicy(DOMUtils.getFirstElement(element));
        policy = policy.normalize(builder.getPolicyRegistry(), false);

        for (Iterator<List<Assertion>> iterator = policy.getAlternatives(); iterator.hasNext();) {
            processAlternative(iterator.next(), supportingToken);
            /*
             * for the moment we will say there should be only one alternative
             */
            break;
        }

        return supportingToken;
    }


    private void processAlternative(List<Assertion> assertions, SupportingToken supportingToken) {

        for (Assertion primitive : assertions) {
            QName qname = primitive.getName();
            String localName = qname.getLocalPart();
            
            if (SPConstants.ALGO_SUITE.equals(localName)) {
                supportingToken.setAlgorithmSuite((AlgorithmSuite)primitive);

            } else if (SPConstants.SIGNED_PARTS.equals(localName)) {
                supportingToken.setSignedParts((SignedEncryptedParts)primitive);

            } else if (SPConstants.SIGNED_ELEMENTS.equals(localName)) {
                supportingToken.setSignedElements((SignedEncryptedElements)primitive);

            } else if (SPConstants.ENCRYPTED_PARTS.equals(localName)) {
                supportingToken.setEncryptedParts((SignedEncryptedParts)primitive);

            } else if (SPConstants.ENCRYPTED_ELEMENTS.equals(localName)) {
                supportingToken.setEncryptedElements((SignedEncryptedElements)primitive);

            } else if (primitive instanceof Token) {
                supportingToken.addToken((Token)primitive);
                ((Token)primitive).setSupportingToken(supportingToken);
            }
        }
    }
}
