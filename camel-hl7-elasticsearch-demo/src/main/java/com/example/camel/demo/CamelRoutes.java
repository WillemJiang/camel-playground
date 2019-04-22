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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ValueBuilder;
import org.apache.camel.component.hl7.HL7;
import org.apache.camel.component.hl7.HL7MLLPNettyDecoderFactory;
import org.apache.camel.component.hl7.HL7MLLPNettyEncoderFactory;
import org.apache.camel.impl.CompositeRegistry;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ca.uhn.hl7v2.model.v24.message.ORU_R01;
import ca.uhn.hl7v2.model.v24.segment.PID;


/**
 * Setup the camel routes here
 */
@Component
public class CamelRoutes extends RouteBuilder {

    private static final String ES_RSS_INDEX_TYPE = "patient";

    public static final String PATIENT_SEARCH_URI = "vm:patientSearch";

    @Value("${elasticsearch.hostaddresses}")
    private String elasticsearchHostaddresses;



    void updateCamelContext(CamelContext camelContext) throws Exception {

        camelContext.getShutdownStrategy().setShutdownRoutesInReverseOrder(false);
        // wait max 5 seconds for camel to stop:
        camelContext.getShutdownStrategy().setTimeout(5L);
        // setup the registry
        CompositeRegistry compositeRegistry = new CompositeRegistry();
        SimpleRegistry simpleRegistry = new SimpleRegistry();
        compositeRegistry.addRegistry(camelContext.getRegistry());
        compositeRegistry.addRegistry(simpleRegistry);
        // Hack the context registry
        ((DefaultCamelContext)camelContext).setRegistry(compositeRegistry);

        HL7MLLPNettyEncoderFactory encoder = new HL7MLLPNettyEncoderFactory();
        encoder.setCharset(Charset.forName("UTF-8"));
        encoder.setConvertLFtoCR(true);

        HL7MLLPNettyDecoderFactory decoder = new HL7MLLPNettyDecoderFactory();
        decoder.setCharset(Charset.forName("UTF-8"));
        decoder.setConvertLFtoCR(true);

        simpleRegistry.put("hl7encoder", new HL7MLLPNettyEncoderFactory().newChannelHandler());
        simpleRegistry.put("hl7decoder", new HL7MLLPNettyDecoderFactory().newChannelHandler());


    }


    @Override
    public void configure() throws Exception {

        String hl7IngressUri = "netty4:tcp://127.0.0.1:8888?sync=true&decoder=#hl7decoder&encoder=#hl7encoder";

        String esIndexUri = String.format("elasticsearch-rest://patient-indexer?operation=Index&hostAddresses=%s",
            elasticsearchHostaddresses);
        String esSearchUri = String
            .format("elasticsearch-rest://patient-indexer?operation=Search&hostAddresses=%s", elasticsearchHostaddresses);

        SplitterBean splitterBean = new SplitterBean();

        ValueBuilder ack = HL7.ack();

        updateCamelContext(getContext());

        //TODO We need to setup the error handler to handle the message

        // HL7 Ingress
        from(hl7IngressUri).id("hl7.ingress")
            .wireTap("seda:auditToFileSystem")
            .log("Get the HL7 Request ${body}")
            .wireTap("seda:elasticSearch")
            // ACK only work with the HAPI message
            .transform(ack);

        from("seda:elasticSearch")
            .process(new ElasticSearchPatientConverter())
            .log("Convert to ElasticSearch Map : ${body}")
            .to(esIndexUri);

        // Store the message into File
        from("seda:auditToFileSystem")
            .to("file://target/audit/ingress?fileName=incoming-audit-${date:now:yyyyMMdd-HH:mm:ss}.hl7");

        // Just search the message from the elastic search service
        from(PATIENT_SEARCH_URI)
            .to(esSearchUri)
            .split(method(splitterBean, "splitSearchHits"), new ResultAggregationStrategy())
                .process(new ElasticSearchSearchHitConverter())
                .to("freemarker:Response.ftl")
            .end()
            // Need to handle if there is no result
            .choice()
                .when().body(body -> body instanceof SearchHits)
                    .to("freemarker:EmptyResultPage.ftl")
                .endChoice()
            .otherwise()
                .to("freemarker:ResultPage.ftl")
            .end();
    }

    class ElasticSearchPatientConverter implements Processor {
        public void process(Exchange exchange) throws Exception {
            Message camelMessage = exchange.getIn();
            // need to setup the message header
            camelMessage.setHeader("indexName", "hl7-patient");
            camelMessage.setHeader("indexType", "test");
            ORU_R01 msg = exchange.getIn().getBody(ORU_R01.class);
            // just take out the HL7 message out into the map
            Map<String, Object> map = new HashMap<>();
            final PID pid = msg.getPATIENT_RESULT().getPATIENT().getPID();
            String familyName = pid.getPatientName()[0].getFamilyName().getFn1_Surname().getValue();
            String givenName = pid.getPatientName()[0].getGivenName().getValue();
            String mailingAddress = pid.getPatientAddress()[0].getStreetAddress().getStreetOrMailingAddress().getValue();

            map.put("familyName", familyName);
            map.put("givenName", givenName);
            map.put("mailingAddress", mailingAddress);

            //TODO we could put other information about patient into map
            exchange.getIn().setBody(map);
        }
    }

    // This class need to be accessed by camel
    public static class SplitterBean {
        public List<SearchHit> splitSearchHits(SearchHits searchHits) {
            List<SearchHit> result = new ArrayList<>();
            for (SearchHit hit : searchHits.getHits()) {
                result.add(hit);
            }
            return result;
        }
    }

    class ResultAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            } else {
                String result = oldExchange.getIn().getBody(String.class);
                String newLine = newExchange.getIn().getBody(String.class);
                result = result + newLine;
                oldExchange.getIn().setBody(result);
                return oldExchange;
            }
        }
    }

    class ElasticSearchSearchHitConverter implements Processor {

        public void process(Exchange exchange) throws Exception {
            SearchHit hit = exchange.getIn().getBody(SearchHit.class);
            // Convert Elasticsearch documents to Maps before serializing to JSON:
            Map<String, Object> map = new HashMap<String, Object>(hit.getSourceAsMap());
            map.put("score", hit.getScore());
            exchange.getIn().setBody(map);
        }
    }

}

