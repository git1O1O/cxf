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

package org.apache.cxf.javascript;

import java.io.IOException;
import java.io.InputStream;

import javax.activation.DataHandler;
import javax.mail.util.ByteArrayDataSource;

import org.apache.cxf.javascript.JavascriptTestUtilities.JSRunnable;
import org.apache.cxf.javascript.JavascriptTestUtilities.Notifier;
import org.apache.cxf.javascript.fortest.MtoM;
import org.apache.cxf.javascript.fortest.MtoMImpl;
import org.apache.cxf.javascript.fortest.MtoMParameterBeanNoDataHandler;
import org.apache.cxf.javascript.fortest.MtoMParameterBeanWithDataHandler;
import org.junit.Before;
import org.junit.Test;
import org.mozilla.javascript.Context;
import org.springframework.context.support.GenericApplicationContext;

/*
 * We end up here with a part with isElement == true, a non-array element, 
 * but a complex type for an array of the element.
 */

public class MtoMTest extends JavascriptRhinoTest {

    MtoMImpl implementor;

    public MtoMTest() throws Exception {
        super();
    }

    @Override
    protected void additionalSpringConfiguration(GenericApplicationContext context) throws Exception {
    }
    
    @Override
    protected String[] getConfigLocations() {
        return new String[] {"classpath:MtoMBeans.xml"};
    }
    
    @Before
    public void before() throws Exception {
        setupRhino("mtom-service-endpoint", 
                   "/org/apache/cxf/javascript/MtoMTests.js",
                   true);
        implementor = (MtoMImpl)rawImplementor;
        implementor.reset();
    }
    
    private Void acceptMtoMString(Context context) throws IOException {
        Notifier notifier = 
            testUtilities.rhinoCallConvert("testMtoMString", Notifier.class, 
                                           testUtilities.javaToJS(getAddress()));
        boolean notified = notifier.waitForJavascript(1000 * 10);
        assertTrue(notified);
        Integer errorStatus = testUtilities.rhinoEvaluateConvert("globalErrorStatus", Integer.class);
        assertNull(errorStatus);
        String errorText = testUtilities.rhinoEvaluateConvert("globalErrorStatusText", String.class);
        assertNull(errorText);
        assertEquals("disorganized<organized", implementor.getLastDHBean().getOrdinary());
        InputStream dis = implementor.getLastDHBean().getNotXml10().getInputStream();
        byte[] bytes = new byte[2048];
        int byteCount = dis.read(bytes, 0, 2048);
        String stuff = new String(bytes, 0, byteCount);
        assertEquals("<html>\u0027</html>", stuff);
        return null;
    }

    @org.junit.Ignore
    @Test
    public void jaxwsClientExperimentExpectAutoBase64() {
        MtoM client = getBean(MtoM.class, "mtom-client");
        assertNotNull(client);
        MtoMParameterBeanNoDataHandler param = new MtoMParameterBeanNoDataHandler();
        param.setOrdinary("blither<blather");
        param.setNotXml10("<html>\u0027</html>");
        client.receiveNonXmlNoDH(param);
        MtoMParameterBeanNoDataHandler got = implementor.getLastBean();
        assertEquals(param.getOrdinary(), got.getOrdinary());
        assertEquals(param.getNotXml10(), got.getNotXml10());
    }

    // doesn't run right in mvn, only a devo tool, anyhow.
    @org.junit.Ignore
    @Test
    public void jaxwsClientExperiment() throws IOException {
        MtoM client = getBean(MtoM.class, "mtom-client");
        assertNotNull(client);
        MtoMParameterBeanWithDataHandler param = new MtoMParameterBeanWithDataHandler();
        param.setOrdinary("blither<blather");
        byte[] notXmlBytes = "Hello\u0027Sailor".getBytes("utf-8");
        DataHandler byteDataHandler = 
            new DataHandler(new ByteArrayDataSource(notXmlBytes,
                            "text/plain"));
        param.setNotXml10(byteDataHandler);
        client.receiveNonXmlDH(param);
        MtoMParameterBeanWithDataHandler got = implementor.getLastDHBean();
        InputStream dis = got.getNotXml10().getInputStream();
        byte[] bytes = new byte[2048];
        int byteCount = dis.read(bytes, 0, 2048);
        String stuff = new String(bytes, 0, byteCount);
        assertEquals(param.getOrdinary(), got.getOrdinary());
        assertEquals("Hello\u0027Sailor", stuff);
    }
    
    @Test
    public void acceptMtoMStringTest() {
        testUtilities.runInsideContext(Void.class, new JSRunnable<Void>() {
            public Void run(Context context) {
                try {
                    return acceptMtoMString(context);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}