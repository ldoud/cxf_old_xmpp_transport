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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.AbstractTransportFactory;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.DestinationFactory;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.EndpointReferenceType;

import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.observer.Listener;
import tigase.jaxmpp.core.client.xmpp.modules.chat.Chat;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule;
import tigase.jaxmpp.core.client.xmpp.modules.chat.MessageModule.MessageEvent;
import tigase.jaxmpp.j2se.Jaxmpp;

/**
 * After receiving a Bus reference this class registers itself as an XMPPDestination. Web service providers
 * that use one of the XMPP URI prefixes will trigger the use of this factory for creation of XMPPDestination.
 * 
 * @author Leon Doud
 */
public class ChatTransportFactory extends AbstractTransportFactory implements DestinationFactory, ConduitInitiator {

    public static final List<String> DEFAULT_NAMESPACES = Arrays
    .asList("http://cxf.apache.org/transports/xmpp/chat");
    
    //private Credentials clientLoginInfo;
  
    public ChatTransportFactory() {
        super();
        setTransportIds(DEFAULT_NAMESPACES);
    }

    /**
     * {@inheritDoc}
     */
    public Destination getDestination(EndpointInfo endpointInfo) {
        AttributedURIType address = new AttributedURIType();
        address.setValue(endpointInfo.getAddress());
        
        EndpointReferenceType epRefType = new EndpointReferenceType();
        epRefType.setAddress(address);
        
        //return new ChatDestination(epRefType, endpointInfo);
        return null;
    }

    @Override
    public Conduit getConduit(EndpointInfo endpointInfo) throws IOException {
        return getConduit(endpointInfo, endpointInfo.getTarget());
    }

    @Override
    public Conduit getConduit(EndpointInfo endpointInfo, EndpointReferenceType endpointType) throws IOException {
        // Create new connection and conduit.
        Jaxmpp contact = new Jaxmpp();
        final ChatConduit conduit = new ChatConduit(endpointType, contact);
        
        // Listen to chat messages that maybe replies to SOAP requests.
        contact.addListener(Chat.MessageReceived, new Listener<MessageModule.MessageEvent>() {
            @Override
            public void handleEvent(MessageEvent mesgReceived) throws JaxmppException {
                conduit.handleResponseMesg(mesgReceived);
            }
        });        
        
        return conduit;
    }
    
//    public void setClientCredentials(Credentials creds) {
//        clientLoginInfo = creds;
//    }

}
