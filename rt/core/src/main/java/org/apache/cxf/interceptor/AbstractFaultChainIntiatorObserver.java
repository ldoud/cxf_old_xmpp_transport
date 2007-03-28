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

package org.apache.cxf.interceptor;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.cxf.Bus;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.transport.MessageObserver;

public abstract class AbstractFaultChainIntiatorObserver implements MessageObserver {
    
    private static final Logger LOG = Logger.getLogger(AbstractFaultChainIntiatorObserver.class.getName());
    
    private Bus bus;

    public AbstractFaultChainIntiatorObserver(Bus bus) {
        this.bus = bus;
    }

    public void onMessage(Message m) {
        Message faultMessage = getFaultMessage(m);
        Exchange ex = m.getExchange();
        if (faultMessage == null) {
            Endpoint ep = ex.get(Endpoint.class);
            faultMessage = ep.getBinding().createMessage();
        }
        
        setFaultMessage(m, faultMessage);
        MessageImpl.copyContent(m, faultMessage);

        // setup chain
        PhaseInterceptorChain chain = new PhaseInterceptorChain(getPhases());
        initializeInterceptors(faultMessage.getExchange(), chain);
        
        faultMessage.setInterceptorChain(chain);
        try {
            chain.doIntercept(faultMessage);
        } catch (Exception exc) {
            LogUtils.log(LOG, Level.INFO, "Error occured during error handling, give up!", ex);
        }
    }

    protected abstract boolean isOutboundObserver();

    protected abstract List<Phase> getPhases();

    protected void initializeInterceptors(Exchange ex, PhaseInterceptorChain chain) {
        
    }

    public Bus getBus() {
        return bus;
    }

    private Message getFaultMessage(Message original) {
        return isOutboundObserver()
               ? original.getExchange().getOutFaultMessage()
               : original.getExchange().getInFaultMessage();
    }

    private void setFaultMessage(Message original, Message fault) {
        if (isOutboundObserver()) {
            original.getExchange().setOutFaultMessage(fault);
        } else {
            original.getExchange().setInFaultMessage(fault);
        }
    }
}
