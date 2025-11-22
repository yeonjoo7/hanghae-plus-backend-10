package com.hanghae.ecommerce;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(basePackages = {
        "com.hanghae.ecommerce.application",
        "com.hanghae.ecommerce.domain",
        "com.hanghae.ecommerce.presentation",
        "com.hanghae.ecommerce.infrastructure",
        "com.hanghae.ecommerce.common"
}, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.hanghae\\.ecommerce\\.domain\\..*\\.repository\\..*"))
public class EcommerceApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(EcommerceApiApplication.class, args);
    }
}