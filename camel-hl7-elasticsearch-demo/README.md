# Getting Started

This demo accepts the HL7 messages and stores them into elasticsearch and local directory for auditing.
There is a RESTful search service for the client to access the stored data.

## Using HAPI TestPanel

1. Download TestPanel from

2. Create Sample project and Connect to localhost:8888 to send the request to the backend server.

## Installing ElasticSearch

1. Download Elasticsearch from https://www.elastic.co/downloads/elasticsearch.

2. Install it to a local folder ($ES_HOME)Edit $ES_HOME/config/elasticsearch.yml and add this line:

		cluster.name: patient-indexer

3. Run Elasticsearch: $ES_HOME/bin/elasticsearch.sh or $ES_HOME/bin/elasticsearch.bat

## Configurations

    #The elastic search server address
    elasticsearch.hostaddresses=127.0.0.1:9200

## Running the Demo
You just need to run the Application.main() from your favourite IDE or execute below line from the command line:

		mvn compile && mvn spring-boot:run

 Users can query the RSS title with a key word from a RESTful service by accessing the service "http://localhost:8080/patient/search?q=xxx&max=10" from .

