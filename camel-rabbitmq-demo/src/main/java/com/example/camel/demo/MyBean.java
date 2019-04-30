package com.example.camel.demo;

import org.springframework.stereotype.Component;

@Component
public class MyBean {
  public String generate() {
    return "Hello Camel!";
  }
}
