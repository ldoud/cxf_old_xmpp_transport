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

package org.apache.cxf.transport.xmpp.common;

import java.io.IOException;

import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.jivesoftware.smack.XMPPConnection;

public class AbstractDestination implements Destination, XMPPConnectionUser {

    // XMPP connection that might be shared with other destinations.
    private XMPPConnection connection;

    // Values initialized during construction.
    private EndpointReferenceType epRefType = new EndpointReferenceType();

    // After messages are received they are passed to this observer.
    private MessageObserver msgObserver;

    public AbstractDestination(EndpointInfo epInfo) {
        // Initialize the address of the epRefType member.
        AttributedURIType address = new AttributedURIType();
        address.setValue(epInfo.getAddress());
        epRefType.setAddress(address);
    }

    /**
     * Required by the Destination interface.
     * 
     * @see org.apache.cxf.transport.Destination
     */
    @Override
    public void setMessageObserver(MessageObserver observer) {
        msgObserver = observer;
    }

    /**
     * Save the information to handle shutdown of non-shared connection.
     * 
     * @see XMPPConnectionUser {@inheritDoc}
     */
    @Override
    public void setXmppConnection(XMPPConnection conn) {
        connection = conn;
    }

    /**
     * {@inheritDoc}
     */
    public XMPPConnection getXmppConnection() {
        return connection;
    }

    /**
     * Required by the Destination interface.
     * 
     * @see org.apache.cxf.transport.Destination
     */
    @Override
    public EndpointReferenceType getAddress() {
        return epRefType;
    }

    /**
     * Not used. The back channel is set on the exchange of the message when the message is received. Required
     * by the Destination interface.
     * 
     * @see org.apache.cxf.transport.Destination
     */
    @Override
    public Conduit getBackChannel(Message inMsg, Message notUsedMsg, EndpointReferenceType notUsedEpRefType)
        throws IOException {
        return null;
    }

    /**
     * Required by the Destination interface.
     * 
     * @see org.apache.cxf.transport.Destination
     */
    @Override
    public MessageObserver getMessageObserver() {
        return msgObserver;
    }

    /**
     * Close the connection if its not shared.
     * 
     * @see org.apache.cxf.transport.Destination {@inheritDoc}
     */
    @Override
    public void shutdown() {
        // Nothing
    }

}
