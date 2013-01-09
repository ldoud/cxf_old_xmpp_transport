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

package org.apache.cxf.transport.xmpp.connection;

import javax.security.auth.callback.CallbackHandler;

import org.apache.cxf.transport.xmpp.messaging.MessageReceiptStrategy;

/**
 * Isolate the XMPP API from the transport code.
 */
public interface ConnectionStrategy {
    
    /**
     * This must be set before activating the connection. 
     * @param xmppServerName The name or IP of the XMPP server
     */
    void setServer(String xmppServerName);
    
    /**
     * This must be set before activating the connection. 
     * @param userCredentials The credentials used to log into the XMPP server
     */
    void setAuthorizationMechanism(CallbackHandler userCredentials);
    
    /**
     * Request the XMPP connection is made.
     * @return True if connection was just activated or was previously active
     */
    boolean activate();

    /**
     * Request the XMPP connection is closed.
     */
    void deactivate();
    
    void registerListener(MessageReceiptStrategy xmppListener);
    
    void unregisterListener(MessageReceiptStrategy xmppListener);
}
