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

package org.apache.cxf.systest.ws.policy;

import java.io.InputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.cxf.helpers.DOMUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.service.model.MessageInfo.Type;
import org.apache.cxf.systest.ws.common.SecurityTestUtil;
import org.apache.cxf.systest.ws.policy.server.JavaFirstPolicyServer;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.cxf.ws.policy.PolicyConstants;
import org.apache.neethi.Constants;

import org.junit.BeforeClass;

public class JavaFirstPolicyServiceTest extends AbstractBusClientServerTestBase {
    static final String PORT = allocatePort(JavaFirstPolicyServer.class);
    static final String PORT2 = allocatePort(JavaFirstPolicyServer.class, 2);
    
    private static final String WSDL_NAMESPACE = "http://schemas.xmlsoap.org/wsdl/";

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue("Server failed to launch",
        // run the server in the same process
        // set this to false to fork
                   launchServer(JavaFirstPolicyServer.class, true));
    }

    @org.junit.AfterClass
    public static void cleanup() throws Exception {
        SecurityTestUtil.cleanup();
        stopAllServers();
    }

    @org.junit.Test
    public void testJavaFirstAttachmentWsdl() throws Exception {
        Document doc = loadWsdl("JavaFirstAttachmentPolicyService");

        Element binding = DOMUtils.getFirstChildWithName(doc.getDocumentElement(), WSDL_NAMESPACE, "binding");
        assertNotNull(binding);
        
        List<Element> operationMessages = DOMUtils.getChildrenWithName(binding, WSDL_NAMESPACE, "operation");
        assertEquals(4, operationMessages.size());
        
        Element doOperationLevelPolicy = getOperationElement("doOperationLevelPolicy", operationMessages);
        assertEquals("#UsernameToken", 
                     getOperationPolicyReferenceId(doOperationLevelPolicy, Constants.URI_POLICY_13_NS));
        
        Element doInputMessagePolicy = getOperationElement("doInputMessagePolicy", operationMessages);
        assertEquals("#UsernameToken", 
                     getMessagePolicyReferenceId(doInputMessagePolicy, Type.INPUT, Constants.URI_POLICY_13_NS));
        assertNull(getMessagePolicyReferenceId(
                                               doInputMessagePolicy, Type.OUTPUT, Constants.URI_POLICY_13_NS));
        
        Element doOutputMessagePolicy = getOperationElement("doOutputMessagePolicy", operationMessages);
        assertEquals("#UsernameToken", 
                     getMessagePolicyReferenceId(doOutputMessagePolicy, Type.OUTPUT, Constants.URI_POLICY_13_NS));
        assertNull(getMessagePolicyReferenceId(
                                               doOutputMessagePolicy, Type.INPUT, Constants.URI_POLICY_13_NS));
        
        Element doNoPolicy = getOperationElement("doNoPolicy", operationMessages);
        assertNull(getMessagePolicyReferenceId(doNoPolicy, Type.INPUT, Constants.URI_POLICY_13_NS));
        assertNull(getMessagePolicyReferenceId(doNoPolicy, Type.OUTPUT, Constants.URI_POLICY_13_NS));
        
        // ensure that the policies are attached to the wsdl:definitions
        List<Element> policyMessages = DOMUtils.getChildrenWithName(doc.getDocumentElement(), 
                                                                    Constants.URI_POLICY_13_NS, "Policy");
        assertEquals(1, policyMessages.size());
        
        assertEquals("UsernameToken", getPolicyId(policyMessages.get(0)));
    }
    
    @org.junit.Test
    public void testJavaFirstWsdl() throws Exception {
        Document doc = loadWsdl("JavaFirstPolicyService");

        Element portType = DOMUtils.getFirstChildWithName(doc.getDocumentElement(), WSDL_NAMESPACE, "portType");
        assertNotNull(portType);
        
        List<Element> operationMessages = DOMUtils.getChildrenWithName(portType, WSDL_NAMESPACE, "operation");
        assertEquals(5, operationMessages.size());
        
        Element operationOne = getOperationElement("doOperationOne", operationMessages);
        assertEquals("#InternalTransportAndUsernamePolicy", 
                     getMessagePolicyReferenceId(operationOne, Type.INPUT, Constants.URI_POLICY_NS));
        Element operationTwo = getOperationElement("doOperationTwo", operationMessages);
        assertEquals("#TransportAndUsernamePolicy", 
                     getMessagePolicyReferenceId(operationTwo, Type.INPUT, Constants.URI_POLICY_NS));
        Element operationThree = getOperationElement("doOperationThree", operationMessages);
        assertEquals("#InternalTransportAndUsernamePolicy", 
                     getMessagePolicyReferenceId(operationThree, Type.INPUT, Constants.URI_POLICY_NS));
        Element operationFour = getOperationElement("doOperationFour", operationMessages);
        assertEquals("#TransportAndUsernamePolicy", 
                     getMessagePolicyReferenceId(operationFour, Type.INPUT, Constants.URI_POLICY_NS));
        Element operationPing = getOperationElement("doPing", operationMessages);
        assertNull(getMessagePolicyReferenceId(operationPing, Type.INPUT, Constants.URI_POLICY_NS));
        
        List<Element> policyMessages = DOMUtils.getChildrenWithName(doc.getDocumentElement(), 
                                                                    Constants.URI_POLICY_NS, "Policy");
        assertEquals(2, policyMessages.size());
        
        // validate that both the internal and external policies are included
        assertEquals("TransportAndUsernamePolicy", getPolicyId(policyMessages.get(0)));
        assertEquals("InternalTransportAndUsernamePolicy", getPolicyId(policyMessages.get(1)));
    }

    private Document loadWsdl(String serviceName) throws Exception {
        HttpURLConnection connection = getHttpConnection("http://localhost:" + PORT2
                                                         + "/" + serviceName + "?wsdl");
        InputStream is = connection.getInputStream();
        String wsdlContents = IOUtils.toString(is);

        return DOMUtils.readXml(new StringReader(wsdlContents));
    }
    
    private String getPolicyId(Element element) {
        return element.getAttributeNS(PolicyConstants.WSU_NAMESPACE_URI,
                                     PolicyConstants.WSU_ID_ATTR_NAME);
    }
    
    private Element getOperationElement(String operationName, List<Element> operationMessages) {
        Element operationElement = null;
        for (Element operation : operationMessages) {
            if (operationName.equals(operation.getAttribute("name"))) {
                operationElement = operation;
                break;
            }
        }
        assertNotNull(operationElement);
        return operationElement;
    }
    
    private String getMessagePolicyReferenceId(Element operationElement, Type type, String policyNamespace) {
        Element messageElement = DOMUtils.getFirstChildWithName(operationElement, WSDL_NAMESPACE, 
                                                              type.name().toLowerCase());
        assertNotNull(messageElement);
        Element policyReference = DOMUtils.getFirstChildWithName(messageElement, policyNamespace, 
                                                                 "PolicyReference");
        if (policyReference != null) {
            return policyReference.getAttribute("URI");
        } else {
            return null;
        }
    }
    
    private String getOperationPolicyReferenceId(Element operationElement, String policyNamespace) {
        Element policyReference = DOMUtils.getFirstChildWithName(operationElement, policyNamespace, 
                                                                 "PolicyReference");
        if (policyReference != null) {
            return policyReference.getAttribute("URI");
        } else {
            return null;
        }
    }
}
