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

package demo.servlet;

import java.io.PrintWriter;
import java.net.URL;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.resource.ResourceException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;

import org.apache.cxf.connector.CXFConnectionFactory;
import org.apache.cxf.connector.Connection;
import org.apache.cxf.jca.cxf.CXFConnectionRequestInfo;
import org.apache.hello_world_soap_http.Greeter;

public class HelloWorldServlet extends DemoServletBase {

    private static final String EIS_JNDI_NAME = "java:comp/env/eis/CXFConnector";

    private String operationName; 
    private String userName; 

    public HelloWorldServlet() {
        super("J2EE Connector Hello World Demo");
    }


    public void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try { 
            operationName = req.getParameter("Operation"); 
            userName = req.getParameter("User"); 
            super.doGet(req, resp);
        } finally { 
            operationName = null; 
        } 
    }


    public void doPost(HttpServletRequest req, HttpServletResponse resp) {
        doGet(req, resp);
    }

    /** 
     * get a connection to the SOAP service
     */ 
    protected Connection getServiceConnection() throws NamingException, ResourceException {

        Context ctx = new InitialContext();

        // retrieve the connection factory from JNDI 
        //
        CXFConnectionFactory factory = (CXFConnectionFactory)ctx.lookup(EIS_JNDI_NAME);
            
        URL wsdlLocation = getClass().getResource("/wsdl/hello_world.wsdl");

        QName serviceName = 
            new QName("http://apache.org/hello_world_soap_http", "SOAPService");
        QName portName = new QName("http://apache.org/hello_world_soap_http", "SoapPort");


        // create the connection 
        //
        CXFConnectionRequestInfo requestInfo = new CXFConnectionRequestInfo(Greeter.class, 
                                                                            wsdlLocation, 
                                                                            serviceName, 
                                                                            portName);
        return (Connection)factory.getConnection(requestInfo);
    }



    protected void writeMainBody(PrintWriter writer) {
        operationForm(writer);
        writer.println("&nbsp;&nbsp;" + getResponseFromWebService());
    }


    protected void operationForm(PrintWriter writer) {

        writer.println("<TABLE ALIGN=\"center\">");
        writer.println("<FORM ACTION=\"./*.do\" METHOD=GET>");
        writer.println("<TR><TD><b>Operation:</b><BR>");
        writer.println("<TR><TD>");   
        writer.println("<INPUT TYPE=RADIO NAME=\"Operation\" VALUE=\"sayHi\"" 
            + ("sayHi".equals(operationName) ? " CHECKED " : "") +  "> sayHi<BR>");
        writer.println("<TR><TD>");
        writer.println("<INPUT TYPE=RADIO NAME=\"Operation\" VALUE=\"greetMe\""
            + ("greetMe".equals(operationName) ? "  CHECKED " : "") + "> greetMe ");
        writer.println("<INPUT TYPE=TEXT NAME=\"User\" VALUE=\""
            + ("greetMe".equals(operationName) ? userName : "") + "\" SIZE=20><BR>");
        writer.println("<TR><TD>");
        writer.println("<INPUT TYPE=SUBMIT VALUE=\"Submit\"><BR></p>");
        writer.println("</FORM>");
        writer.println("</TABLE>");
    }


    String getResponseFromWebService() {

        Greeter greeter = null;
        String responseFromWebService = "No message sent";
        
        try {
            greeter = (Greeter)getServiceConnection();
            
            if (operationName != null) {

                if ("sayHi".equals(operationName)) {
                    responseFromWebService = greeter.sayHi();
                } else if ("greetMe".equals(operationName)) {
                    responseFromWebService = greeter.greetMe(userName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e.getCause() != null) { 
                e.getCause().printStackTrace(); 
            } 
            responseFromWebService = e.toString();
        } finally { 
            try {
                if (greeter != null) {
                    ((Connection)greeter).close();
                }
            } catch (ResourceException e) {
                // report error from close
            }
        } 
        return responseFromWebService; 
    }
}
