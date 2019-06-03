/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.example.camel.demo;

import org.apache.camel.component.hl7.HL7MLLPNettyDecoderFactory;
import org.apache.camel.component.hl7.HL7MLLPNettyEncoderFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// We could setup the beans configuration here
public class CamelConfigure {

    // These beans can be accessed from the camel registry
    @Bean(name="hl7encoder")
    public HL7MLLPNettyEncoderFactory getHL7EncoderFactory() {
        return new HL7MLLPNettyEncoderFactory();
    }

    @Bean(name="hl7decoder")
    public HL7MLLPNettyDecoderFactory getHL7DecoderFactory() {
        return new HL7MLLPNettyDecoderFactory();
    }
}
