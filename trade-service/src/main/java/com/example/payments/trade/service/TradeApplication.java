package com.example.payments.trade.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TradeApplication { public static void main(String[] args) { SpringApplication.run(TradeApplication.class, args); } }
