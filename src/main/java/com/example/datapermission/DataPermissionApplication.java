package com.example.datapermission;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataPermissionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataPermissionApplication.class, args);
    }
}
