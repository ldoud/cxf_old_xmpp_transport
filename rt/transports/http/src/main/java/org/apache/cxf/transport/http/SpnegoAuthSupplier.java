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
package org.apache.cxf.transport.http;

import java.net.URL;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.configuration.security.AuthorizationPolicy;
import org.apache.cxf.message.Message;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

public class SpnegoAuthSupplier extends HttpAuthSupplier {
    private static final String KERBEROS_OID = "1.2.840.113554.1.2.2";
    //private static final String SPNEGO_OID = "1.3.6.1.5.5.2";

    private static final Logger LOG = LogUtils.getL7dLogger(HTTPConduit.class);

    private LoginContext lc;

    @Override
    public String getPreemptiveAuthorization(HTTPConduit conduit, URL currentURL, Message message) {
        AuthorizationPolicy authPolicy = conduit.getEffectiveAuthPolicy(message);
        if (!HttpAuthHeader.AUTH_TYPE_NEGOTIATE.equals(authPolicy.getAuthorizationType())) {
            return null;
        }
        try {
            String spn = "HTTP/" + currentURL.getHost();
            LOG.fine("Adding authorization service ticket for service principal name: " + spn);
            byte[] token = getToken(authPolicy, spn);
            return HttpAuthHeader.AUTH_TYPE_NEGOTIATE + " " + Base64Utility.encode(token);
        } catch (LoginException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (GSSException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public String getAuthorizationForRealm(HTTPConduit conduit, URL currentURL, Message message,
                                           String realm, String fullHeader) {
        return getPreemptiveAuthorization(conduit, currentURL, message);
    }

    /**
     * Create and return service ticket token
     * 
     * @param authPolicy
     * @param context
     * @return
     * @throws GSSException
     * @throws LoginException
     */
    private byte[] getToken(AuthorizationPolicy authPolicy, final GSSContext context) throws GSSException,
            LoginException {
        final byte[] token = new byte[0];

        if (authPolicy.getUserName() == null || authPolicy.getUserName().trim().length() == 0) {
            return context.initSecContext(token, 0, token.length);
        }

        if (lc == null) {
            lc = new LoginContext(authPolicy.getAuthorization(), getUsernamePasswordHandler(
                authPolicy.getUserName(), authPolicy.getPassword()));
            lc.login();
        }

        try {
            return (byte[]) Subject.doAs(lc.getSubject(), new CreateServiceTicketAction(context, token));
        } catch (PrivilegedActionException e) {
            if (e.getCause() instanceof GSSException) {
                throw (GSSException) e.getCause();
            }
            LOG.log(Level.SEVERE, "initSecContext", e);
            return null;
        }
    }

    /**
     * Create and return a service ticket token for a given service principal
     * name
     * 
     * @param proxyAuthPolicy
     * @param spn
     * @return service ticket token
     * @throws GSSException
     * @throws LoginException
     */
    private byte[] getToken(AuthorizationPolicy proxyAuthPolicy, String spn) throws GSSException, 
        LoginException {
        GSSManager manager = GSSManager.getInstance();
        GSSName serverName = manager.createName(spn, null);

        // TODO Is it correct to use kerberos oid instead of spnego here?
        Oid oid = new Oid(KERBEROS_OID);
        
        GSSContext context = manager
                .createContext(serverName.canonicalize(oid), oid, null, GSSContext.DEFAULT_LIFETIME);
        // TODO Do we need mutual auth. Will the code we have really work with
        // mutual auth?
        context.requestMutualAuth(true);
        // TODO Credential delegation could be a security hole if it was not
        // intended. Both settings should be configurable
        context.requestCredDeleg(true);

        return getToken(proxyAuthPolicy, context);
    }

    private final class CreateServiceTicketAction implements PrivilegedExceptionAction<byte[]> {
        private final GSSContext context;
        private final byte[] token;

        private CreateServiceTicketAction(GSSContext context, byte[] token) {
            this.context = context;
            this.token = token;
        }

        public byte[] run() throws GSSException {
            return context.initSecContext(token, 0, token.length);
        }
    }
    
    public static CallbackHandler getUsernamePasswordHandler(final String username, final String password) {
        final CallbackHandler handler = new CallbackHandler() {

            public void handle(final Callback[] callback) {
                for (int i = 0; i < callback.length; i++) {
                    if (callback[i] instanceof NameCallback) {
                        final NameCallback nameCallback = (NameCallback) callback[i];
                        nameCallback.setName(username);
                    } else if (callback[i] instanceof PasswordCallback) {
                        final PasswordCallback passCallback = (PasswordCallback) callback[i];
                        passCallback.setPassword(password.toCharArray());
                    }
                }
            }
        };
        return handler;
    }
}