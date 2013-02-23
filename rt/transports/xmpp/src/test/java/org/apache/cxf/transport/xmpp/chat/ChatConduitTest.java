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

import junit.framework.Assert;

import org.apache.cxf.message.Message;
import org.apache.cxf.transport.MessageObserver;
import org.apache.cxf.transport.xmpp.messaging.ConnectionStrategy;
import org.apache.cxf.ws.addressing.AttributedURIType;
import org.apache.cxf.ws.addressing.EndpointReferenceType;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import test.xmpp.service.HelloWorld;

public class ChatConduitTest {

    @Test
    public void sendMessage() {
        String testJid = "service1@localhost.localdomain";
        AttributedURIType address =  new AttributedURIType();
        address.setValue(testJid);
        
        EndpointReferenceType endPoint = new EndpointReferenceType();
        endPoint.setAddress(address);
     
        // Create a service client that points at service1@localhost.localdomain
        ChatConduit conduit = new ChatConduit(endPoint);
        
        // Give it a connection that can be tested.
        ConnectionStrategy xmppConnection = Mockito.mock(ConnectionStrategy.class);
        conduit.setConnectionStrategy(xmppConnection);
        
        // See if the reply is passed along.
        MessageObserver observer = Mockito.mock(MessageObserver.class);
        conduit.setMessageObserver(observer);
        
        Message msg = Mockito.mock(Message.class);
        try {
            conduit.close(msg);
            
            // Verify the right parameters were used to send the chat message
            Mockito.verify(xmppConnection).sendChatMessage(msg, testJid, observer);
        } catch (IOException e) {
            Assert.fail("Exception while sending the message");
        }
        
    }
    
    public static void main(String[] args) throws Exception {
//        new ClassPathXmlApplicationContext("server-chat-applicationContext.xml");
        
        ClassPathXmlApplicationContext context = 
            new ClassPathXmlApplicationContext("client-chat-applicationContext.xml");
        
        HelloWorld serviceClient = (HelloWorld)context.getBean("helloClient");
        serviceClient.sayHi("testing 1,2,3");
        serviceClient.yell("one way message");
        
        Thread.sleep(30 * 60 * 1000);
    }
}
