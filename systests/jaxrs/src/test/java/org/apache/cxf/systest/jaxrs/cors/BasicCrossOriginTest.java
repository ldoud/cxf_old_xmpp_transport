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

package org.apache.cxf.systest.jaxrs.cors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.cors.CorsHeaderConstants;
import org.apache.cxf.systest.jaxrs.AbstractSpringServer;
import org.apache.cxf.testutil.common.AbstractBusClientServerTestBase;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class BasicCrossOriginTest extends AbstractBusClientServerTestBase {
    public static final String PORT = SpringServer.PORT;
    private WebClient configClient;

    @BeforeClass
    public static void startServers() throws Exception {
        assertTrue("server did not launch correctly", launchServer(SpringServer.class, true));
    }

    @Before
    public void before() {
        List<Object> providers = new ArrayList<Object>();
        providers.add(new org.codehaus.jackson.jaxrs.JacksonJsonProvider());
        configClient = WebClient.create("http://localhost:" + PORT + "/config", providers);
    }

    @Test
    public void testSimpleGet() throws Exception {
        String origin = "http://localhost:" + PORT;
        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet(origin + "/test/simpleGet/HelloThere");
        httpget.addHeader("Origin", origin);
        HttpResponse response = httpclient.execute(httpget);
        HttpEntity entity = response.getEntity();
        String e = IOUtils.toString(entity.getContent(), "utf-8");

        assertEquals("HelloThere", e);
        Header[] aaoHeaders = response.getHeaders(CorsHeaderConstants.HEADER_AC_ALLOW_ORIGIN);
        assertNotNull(aaoHeaders);
        assertEquals(1, aaoHeaders.length);
        assertEquals("*", aaoHeaders[0].getValue());
    }
    
    private List<String> headerValues(Header[] headers) {
        List<String> values = new ArrayList<String>();
        for (Header h : headers) {
            for (HeaderElement e : h.getElements()) {
                values.add(e.getName());
            }
        }
        return values;
    }

    private void assertAllOrigin(boolean allOrigins, String[] originList, String[] requestOrigins,
                                 boolean permitted) throws ClientProtocolException, IOException {
        if (allOrigins) {
            originList = new String[0];
        }
        // tell filter what to do.
        String confResult = configClient.accept("text/plain").replacePath("/setOriginList")
            .type("application/json").post(originList, String.class);
        assertEquals("ok", confResult);

        HttpClient httpclient = new DefaultHttpClient();
        HttpGet httpget = new HttpGet("http://localhost:" + PORT + "/test/simpleGet/HelloThere");
        if (requestOrigins != null) {
            for (String requestOrigin : requestOrigins) {
                httpget.addHeader("Origin", requestOrigin);
            }
        }
        HttpResponse response = httpclient.execute(httpget);
        HttpEntity entity = response.getEntity();
        String e = IOUtils.toString(entity.getContent(), "utf-8");

        assertEquals("HelloThere", e); // ensure that we didn't bust the operation itself.
        Header[] aaoHeaders = response.getHeaders(CorsHeaderConstants.HEADER_AC_ALLOW_ORIGIN);
        if (permitted) {
            assertNotNull(aaoHeaders);
            if (allOrigins) {
                assertEquals(1, aaoHeaders.length);
                assertEquals("*", aaoHeaders[0].getValue());
            } else {
                List<String> ovalues = headerValues(aaoHeaders);
                assertEquals(requestOrigins.length, ovalues.size());
                for (int x = 0; x < requestOrigins.length; x++) {
                    assertEquals(requestOrigins[x], ovalues.get(x));
                }
            }
        } else {
            // Origin: null? We don't use it and it's not in the CORS spec.
            assertTrue(aaoHeaders == null || aaoHeaders.length == 0);
        }
    }

    @Test
    public void allowStarPassOne() throws Exception {
        // Allow *, pass origin
        assertAllOrigin(true, null, new String[] {
            "http://localhost:" + PORT
        }, true);
    }
    
    @Test
    public void allowStarPassNone() throws Exception {
        // allow *, no origin
        assertAllOrigin(true, null, null, false);
    }
    
    @Test
    public void allowOnePassOne() throws Exception {
        // allow one, pass that one
        assertAllOrigin(false, new String[] {
            "http://localhost:" + PORT
        }, new String[] {
            "http://localhost:" + PORT
        }, true);
    } 
    
    @Test
    public void allowOnePassWrong() throws Exception {
        // allow one, pass something else
        assertAllOrigin(false, new String[] {
            "http://localhost:" + PORT
        }, new String[] {
            "http://area51.mil:31315",
        }, false);
    }
    
    @Test
    public void allowTwoPassOne() throws Exception {
        // allow two, pass one
        assertAllOrigin(false, new String[] {
            "http://localhost:" + PORT, "http://area51.mil:3141"
        }, new String[] {
            "http://localhost:" + PORT
        }, true);
    }
    
    @Test
    public void allowTwoPassTwo() throws Exception {
        // allow two, pass two
        assertAllOrigin(false, new String[] {
            "http://localhost:" + PORT, "http://area51.mil:3141"
        }, new String[] {
            "http://localhost:" + PORT, "http://area51.mil:3141"
        }, true);
    }
    
    @Test
    public void allowTwoPassThree() throws Exception {
        // allow two, pass three
        assertAllOrigin(false, new String[] {
            "http://localhost:" + PORT, "http://area51.mil:3141"
        }, new String[] {
            "http://localhost:" + PORT, "http://area51.mil:3141", "http://hogwarts.edu:9"
        }, false);

    }

    @Ignore
    public static class SpringServer extends AbstractSpringServer {
        public static final String PORT = AbstractSpringServer.PORT;

        public SpringServer() {
            super("/jaxrs_cors");
        }
    }
}
