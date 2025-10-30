package com.hulahoop.blueback;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.hulahoop.blueback.member.model.dao")
public class BlueBackApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlueBackApplication.class, args);
    }

}
