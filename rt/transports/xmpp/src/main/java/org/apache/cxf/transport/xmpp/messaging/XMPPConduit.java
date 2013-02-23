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

package org.apache.cxf.transport.xmpp.messaging;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.AbstractConduit;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

public abstract class XMPPConduit extends AbstractConduit {
    
    private static final Logger LOGGER = LogUtils.getLogger(XMPPConduit.class);
    
    private ConnectionStrategy xmppConnection;

    public XMPPConduit(EndpointReferenceType refType) {
        super(refType);
    }
    
    public void setConnectionStrategy(ConnectionStrategy strat) {
        xmppConnection = strat;
    }
    
    public ConnectionStrategy getConnectionStrategy() {
        return xmppConnection;
    }
    
    @Override
    protected void activate() {
        super.activate();
           
        // Calling Client.getConduit() will eventually call this.activate()
        // Which means that any feature that calls Client.getConduit() will activate the client.
        // Most likely xmppConnection will be null when activate() is called because of this.
        xmppConnection.activate();
        
        getLogger().info("Client started: " 
            + getTarget().getAddress());
    }
    
    @Override
    protected void deactivate() {
        super.deactivate();
        
        xmppConnection.deactivate();
        
        getLogger().info("Client started: " 
            + getTarget().getAddress());
    }
    
    @Override
    public void prepare(Message msg) throws IOException {
        msg.setContent(OutputStream.class, new CachedOutputStream());
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
}
