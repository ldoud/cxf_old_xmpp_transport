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

import java.io.OutputStream;

import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Message;

import org.junit.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class XMPPReplyChannelTest {

    @Test
    public void closeMessage() throws Exception {
        // Setup reply channel with a mock chat session.
        MessageReplyStrategy replyStrategy = mock(MessageReplyStrategy.class);
        XMPPReplyChannel replyChannel = new XMPPReplyChannel(replyStrategy);
        
        // Create test CXF message.
        String message = "<soap>foo</soap>";
        CachedOutputStream outputStream = new CachedOutputStream();
        outputStream.write(message.getBytes());
        Message cxfMessage = mock(Message.class);
        when(cxfMessage.getContent(OutputStream.class)).thenReturn(outputStream);
        
        // Trigger test method.
        replyChannel.close(cxfMessage);
        
        // Verify the observer was triggered once with correct contents
        verify(replyStrategy, times(1)).sendReplyMessage(message);
        
        outputStream.close();
    }
    
    @Test
    public void prepareMessage() throws Exception {
        // Setup reply channel with a mock chat session.
        MessageReplyStrategy replyStrategy = mock(MessageReplyStrategy.class);
        XMPPReplyChannel replyChannel = new XMPPReplyChannel(replyStrategy);
        
        // Create test CXF message.
        Message cxfMessage = mock(Message.class);
        
        // Trigger test method.
        replyChannel.prepare(cxfMessage);
        
        // Verify the observer was triggered once with correct contents
        verify(cxfMessage, times(1)).setContent(any(Class.class), any(CachedOutputStream.class));        
    }
}
