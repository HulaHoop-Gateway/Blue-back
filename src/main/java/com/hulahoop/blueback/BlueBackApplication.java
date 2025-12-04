package com.hulahoop.blueback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.hulahoop.blueback")
public class BlueBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlueBackApplication.class, args);
        System.out.println("BlueBackApplication Started - Scheduling Enabled");
    }

}
