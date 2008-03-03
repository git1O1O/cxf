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

package org.apache.cxf.systest.jaxrs;

import javax.ws.rs.ConsumeMime;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.ProduceMime;
import javax.ws.rs.UriParam;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

@Path("/petstore/")
public class PetStore {

    public static final String CLOSED = "The Pet Store is closed";

    public PetStore() {
        System.out.println("Petstore constructed");
    }

    @GET
    @Path("/pets/{petId}/")
    @ProduceMime("text/xml")
    public Response getStatus(@UriParam("petId")
                              String petId) throws Exception {
        System.out.println("----invoking getStatus on the petStore for id: " + petId);

        return Response.ok(CLOSED).build();
    }

    @POST
    @Path("/pets/")
    @ConsumeMime("application/x-www-form-urlencoded")
    @ProduceMime("text/xml")
    public Response updateStatus(MultivaluedMap<String, String> params) throws Exception {
        System.out.println("----invoking updateStatus on the petStore with stauts post param value of: "
                           + params.getFirst("status"));

        return Response.ok(params.getFirst("status")).build();
    }
}
