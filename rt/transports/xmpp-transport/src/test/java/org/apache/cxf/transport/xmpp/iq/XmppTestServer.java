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
package org.apache.cxf.transport.xmpp.iq;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.junit.Assert;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class XmppTestServer {
    
    private String xmppServiceName = "localhost"; //"localhost.localdomain";

    public void startActiveMQ() {
        new ClassPathXmlApplicationContext("xmpp-embedded-broker.xml");
    }
    
    public XMPPConnection connect(String username, String password) {
        XMPPConnection xmppConnection = 
            new XMPPConnection(new ConnectionConfiguration(xmppServiceName, 61222));
        
        try {
            // Login to the XMMP server using the username
            // and password from the configuration.
            xmppConnection.connect();
            xmppConnection.login(username, password);
        } catch (XMPPException xmppError) {
            xmppError.printStackTrace();
            Assert.fail("Couldn't login");
        }
        
        return xmppConnection;
    }
}
