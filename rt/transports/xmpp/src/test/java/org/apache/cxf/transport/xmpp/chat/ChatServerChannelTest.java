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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import junit.framework.Assert;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.MessageObserver;

import org.jivesoftware.smack.Chat;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ChatServerChannelTest {

    @Test
    public void processMessage() throws Exception {
        ChatServerChannel channel = new ChatServerChannel();
        
        // Setup a mock message observer that will get the message.
        MessageObserver msgObserver = Mockito.mock(MessageObserver.class);
        channel.setMessageObserver(msgObserver);
        
        // Create a fake message message and mock chat session.
        String message = "<soap>foo</soap>";
        Chat chatSession = Mockito.mock(Chat.class);
        org.jivesoftware.smack.packet.Message chatMessage = new org.jivesoftware.smack.packet.Message();
        chatMessage.setBody(message);
        
        // Send a fake message.
        channel.processMessage(chatSession, chatMessage);
        
        // Verify the observer was triggered once.
        ArgumentCaptor<Message> argument = ArgumentCaptor.forClass(Message.class);
        Mockito.verify(msgObserver, Mockito.times(1)).onMessage(argument.capture());
        
        // Verify the exchange created.
        Mockito.verify(msgObserver).onMessage(argument.capture());
        Exchange msgExchange = argument.getValue().getExchange();
        Assert.assertNotNull("Message exchange was created", msgExchange);
        
        // Verify the message contents.
        InputStream msgContents = argument.getValue().getContent(InputStream.class);
        BufferedReader lineReader = new BufferedReader(new InputStreamReader(msgContents));
        Assert.assertEquals("Message contents set", message, lineReader.readLine());
        Assert.assertNull("Message was only one line", lineReader.readLine());
        
        // Verify that the reply channel was created.
        Conduit replyChannel = msgExchange.getConduit(argument.getValue());
        Assert.assertNotNull("Reply channel created", replyChannel);
        Assert.assertTrue("Replying via chat", replyChannel instanceof ChatServerReplyChannel);
    }
    
    public static void main(String[] args) throws Exception {
        new ClassPathXmlApplicationContext("server-chat-applicationContext.xml");
        
        Thread.sleep(30 * 60 * 1000);
    }
}
