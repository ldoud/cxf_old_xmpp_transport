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
import java.io.OutputStream;
import java.util.logging.Logger;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.apache.cxf.wsdl.EndpointReferenceUtils;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.XMPPException;

public class ChatServerReplyChannel implements Conduit {
    
    private static final Logger LOGGER = LogUtils.getLogger(ChatServerReplyChannel.class);
    
    private MessageObserver msgObserver;
    private Chat xmppChat;

    public ChatServerReplyChannel(Chat chat) {
        xmppChat = chat;
    }

    @Override
    public MessageObserver getMessageObserver() {
        return msgObserver;
    }

    @Override
    public void setMessageObserver(MessageObserver observer) {
        msgObserver = observer;
    }

    @Override
    public void close() {
        // The XMPP connection stays open long after the reply conduit.
        // The connection will be closed later by the server's destination object.
    }

    /**
     * The resources for this message should be closed. 
     * This will trigger the writing of the SOAP response to the client.
     */
    @Override
    public void close(Message msg) throws IOException {
        CachedOutputStream soapResponse = (CachedOutputStream)msg.getContent(OutputStream.class);
        StringBuilder replyMsg = new StringBuilder();
        soapResponse.writeCacheTo(replyMsg);

        try {
            LOGGER.info("Sending chat response: " + replyMsg.toString());
            xmppChat.sendMessage(replyMsg.toString());
        } catch (XMPPException e) {
            throw new IOException(e);
        }
    }

    @Override
    public EndpointReferenceType getTarget() {
        return EndpointReferenceUtils.getAnonymousEndpointReference();
    }

    /**
     * Puts an output stream in the message. 
     * The interceptors will write the response into this output stream.
     */
    @Override
    public void prepare(Message msg) throws IOException {
        msg.setContent(OutputStream.class, new CachedOutputStream());
    }

}
