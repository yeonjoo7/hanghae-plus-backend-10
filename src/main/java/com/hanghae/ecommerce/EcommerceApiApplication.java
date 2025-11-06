package com.hanghae.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "com.hanghae.ecommerce.application",
    "com.hanghae.ecommerce.domain", 
    "com.hanghae.ecommerce.presentation",
    "com.hanghae.ecommerce.infrastructure",
    "com.hanghae.ecommerce.common"
})
public class EcommerceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(EcommerceApiApplication.class, args);
    }
}