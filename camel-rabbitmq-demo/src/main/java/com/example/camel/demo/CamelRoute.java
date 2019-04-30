package com.example.camel.demo;

import org.apache.camel.builder.RouteBuilder;

import org.apache.camel.component.rabbitmq.RabbitMQComponent;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.ConnectionFactory;

@Component
public class CamelRoute extends RouteBuilder {

  @Override
  public void configure() throws Exception {
    //We could setup the rabbitmq connection here
    ConnectionFactory connectionFactory = new ConnectionFactory();
    connectionFactory.setHost("localhost");
    connectionFactory.setPort(5672);
    connectionFactory.setUsername("guest");
    connectionFactory.setPassword("guest");
    RabbitMQComponent component = new RabbitMQComponent();
    component.setConnectionFactory(connectionFactory);

    getContext().addComponent("rabbitmq-1", component);

    // We could remove camel component if we don't need the route
    // getContext().removeComponent("rabbitmq-1");


    from("timer:hello?period=1000")
        .transform(simple("Random number ${random(0,100)}"))
        .to("rabbitmq-1:foo");

    from("rabbitmq-1:foo")
          .log("From RabbitMQ: ${body}");
  }
}
