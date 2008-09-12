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

package org.apache.cxf.jaxrs;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.ConsumeMime;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ProduceMime;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.MessageBodyWorkers;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.cxf.jaxrs.impl.PathSegmentImpl;

public class Customer {
    
    @XmlRootElement(name = "CustomerBean")
    public static class CustomerBean {
        private String a;
        private Long b;
        public void setA(String aString) {
            this.a = aString;
        }
        public void setB(Long bLong) {
            this.b = bLong;
        }
        public String getA() {
            return a;
        }
        public Long getB() {
            return b;
        }
        
    }
    
    @Context private ContextResolver<JAXBContext> cr;
    private UriInfo uriInfo;
    @Context private HttpHeaders headers;
    @Context private Request request;
    @Context private SecurityContext sContext;
    @Context private MessageBodyWorkers bodyWorkers;
    
    @Resource private HttpServletRequest servletRequest;
    @Resource private HttpServletResponse servletResponse;
    @Resource private ServletContext servletContext;
    @Context private HttpServletRequest servletRequest2;
    @Context private HttpServletResponse servletResponse2;
    @Context private ServletContext servletContext2;
    
    @Context private UriInfo uriInfo2;
    private String queryParam;
    
    @QueryParam("b")
    private String b;
    
    public String getB() {
        return b;
    }
    
    public void testQueryBean(@QueryParam("") CustomerBean cb) {
        
    }
    public void testPathBean(@PathParam("") CustomerBean cb) {
        
    }
    
    public UriInfo getUriInfo() {
        return uriInfo;
    }
    public UriInfo getUriInfo2() {
        return uriInfo2;
    }
    
    @Context
    public void setUriInfo(UriInfo ui) {
        uriInfo = ui;
    }
    
    @QueryParam("a")
    public void setA(String a) {
        queryParam = a;
    }
    
    public String getQueryParam() {
        return queryParam;
    }
    
    public HttpHeaders getHeaders() {
        return headers;
    }
    
    public Request getRequest() {
        return request;
    }
    
    public MessageBodyWorkers getBodyWorkers() {
        return bodyWorkers;
    }
    
    public SecurityContext getSecurityContext() {
        return sContext;
    }
    
    public HttpServletRequest getServletRequest() {
        return servletRequest2;
    }
    
    public HttpServletResponse getServletResponse() {
        return servletResponse2;
    }
    
    public ServletContext getServletContext() {
        return servletContext2;
    }
    
    public HttpServletRequest getServletRequestResource() {
        return servletRequest;
    }
    
    public HttpServletResponse getServletResponseResource() {
        return servletResponse;
    }
    
    public ServletContext getServletContextResource() {
        return servletContext;
    }
    
    public ContextResolver getContextResolver() {
        return cr;
    }

    @ProduceMime("text/xml")
    @ConsumeMime("text/xml")
    public void test() {
        // complete
    }
    
    @ProduceMime("text/xml")   
    public void getItAsXML() {
        // complete
    }
    @ProduceMime("text/plain")   
    public void getItPlain() {
        // complete
    }
    
    @ProduceMime("text/xml")   
    public void testQuery(@QueryParam("query") String queryString, 
                          @QueryParam("query") int queryInt) {
        // complete
    }
    
    @ProduceMime("text/xml")   
    public void testMultipleQuery(@QueryParam("query")  String queryString, 
                                  @QueryParam("query2") String queryString2,
                                  @QueryParam("query3") Long queryString3,
                                  @QueryParam("query4") boolean queryBoolean4,
                                  @QueryParam("query5") String queryString4) {
        // complete
    }
    
    @ProduceMime("text/xml")   
    public void testMatrixParam(@MatrixParam("p1")  String queryString, 
                                @MatrixParam("p2") String queryString2) {
        // complete
    }
    
    public void testParams(@Context UriInfo info,
                           @Context HttpHeaders hs,
                           @Context Request r,
                           @Context SecurityContext s,
                           @Context MessageBodyWorkers workers,
                           @HeaderParam("Foo") String h) {
        // complete
    }
    
    public void testServletParams(@Context HttpServletRequest req,
                                  @Context HttpServletResponse res,
                                  @Context ServletContext context) {
        // complete
    }
    
    @Path("{id1}/{id2}")
    public void testConversion(@PathParam("id1") PathSegmentImpl id1,
                               @PathParam("id2") SimpleFactory f) {
        // complete
    }
    
    public void testContextResolvers(@Context ContextResolver<JAXBContext> resolver) {
        // complete
    }
};