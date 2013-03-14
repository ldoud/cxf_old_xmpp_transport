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

package org.apache.cxf.transport.xmpp.chat;

import java.util.logging.Logger;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractDestination;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

import tigase.jaxmpp.j2se.Jaxmpp;

public class ChatDestination extends AbstractDestination implements Destination {

    private static final Logger LOGGER = LogUtils.getLogger(ChatDestination.class);

    private Jaxmpp xmppConnection = new Jaxmpp();
    
    public ChatDestination(EndpointReferenceType ref, EndpointInfo ei, Jaxmpp connectionToXmppServer) {
        super(ref, ei);
        xmppConnection = connectionToXmppServer;
    }

    @Override
    protected Conduit getInbuiltBackChannel(Message msg) {
        return null;
    }

    @Override
    protected Logger getLogger() {
        return LOGGER;
    }
    
    
}
