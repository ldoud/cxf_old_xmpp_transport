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

package org.apache.cxf.rt.security.xacml;

import java.security.Principal;
import java.util.HashSet;
import java.util.Set;

import javax.security.auth.Subject;

import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.security.LoginSecurityContext;
import org.apache.cxf.security.SecurityContext;
import org.apache.ws.security.saml.ext.OpenSAMLUtil;


/**
 * Some unit tests to test the AbstractXACMLAuthorizingInterceptor.
 */
public class XACMLAuthorizingInterceptorTest extends org.junit.Assert {
    
    static {
        OpenSAMLUtil.initSamlEngine();
    }

    @org.junit.Test
    public void testPermit() throws Exception {
        // Mock up a Security Context
        SecurityContext sc = createSecurityContext("alice", "manager");
        
        String operation = "{http://www.example.org/contract/DoubleIt}DoubleIt";
        MessageImpl msg = new MessageImpl();
        msg.put(Message.WSDL_OPERATION, operation);
        msg.put(SecurityContext.class, sc);
        
        XACMLAuthorizingInterceptor authorizingInterceptor = 
            new XACMLAuthorizingInterceptor();
        authorizingInterceptor.handleMessage(msg);
    }
    
    @org.junit.Test
    public void testDeny() throws Exception {
        // Mock up a Security Context
        SecurityContext sc = createSecurityContext("alice", "boss");
        
        String operation = "{http://www.example.org/contract/DoubleIt}DoubleIt";
        MessageImpl msg = new MessageImpl();
        msg.put(Message.WSDL_OPERATION, operation);
        msg.put(SecurityContext.class, sc);
        
        XACMLAuthorizingInterceptor authorizingInterceptor = 
            new XACMLAuthorizingInterceptor();
        
        try {
            authorizingInterceptor.handleMessage(msg);
            fail("Failure expected on deny");
        } catch (Exception ex) {
            // Failure expected
        }
    }
    
    private SecurityContext createSecurityContext(final String user, final String role) {
        return new LoginSecurityContext() {

            @Override
            public Principal getUserPrincipal() {
                return new Principal() {
                    public String getName() {
                        return user;
                    }
                };
            }

            @Override
            public boolean isUserInRole(String role) {
                return false;
            }

            @Override
            public Subject getSubject() {
                return null;
            }

            @Override
            public Set<Principal> getUserRoles() {
                Set<Principal> principals = new HashSet<Principal>();
                principals.add(new Principal() {
                    public String getName() {
                        return role;
                    }
                });
                return principals;
            }
            
        };
    }
    
}
