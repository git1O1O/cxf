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
package yoko.server;

import yoko.common.HelloWorld;


@javax.jws.WebService(portName = "HelloWorldCORBAPort", serviceName = "HelloWorldCORBAService", 
                      targetNamespace = "http://schemas.apache.org/yoko/idl/HelloWorld",
                      wsdlLocation = "file:./build/HelloWorld-corba.wsdl",
                      endpointInterface = "yoko.common.HelloWorld")
                      
public class HelloWorldImpl implements HelloWorld {

    public java.lang.String greetMe(java.lang.String inparameter) {
        System.out.println("In greetMe(" + inparameter + ")");
        return "Hi " + inparameter;
    }

}