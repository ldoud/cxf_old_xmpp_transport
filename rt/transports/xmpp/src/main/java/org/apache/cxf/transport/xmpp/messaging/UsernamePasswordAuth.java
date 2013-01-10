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

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.sasl.SASLMechanism;

public class UsernamePasswordAuth extends SASLMechanism {
//implements CallbackHandler {
    
    public UsernamePasswordAuth(SASLAuthentication saslAuthentication) {
        super(saslAuthentication);
        authenticationId = "service1";
        password = "service1";
    }

    @Override
    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
        super.handle(callbacks);
        
        for (Callback cb : callbacks) {
            System.out.println("Callback: " + cb.getClass().getName());
            if (cb instanceof PasswordCallback) {
                PasswordCallback pwdCallback = (PasswordCallback)cb;
                pwdCallback.setPassword("password".toCharArray());
            }
        }
    }

    @Override
    protected String getName() {
        return "userpass";
    }

}
