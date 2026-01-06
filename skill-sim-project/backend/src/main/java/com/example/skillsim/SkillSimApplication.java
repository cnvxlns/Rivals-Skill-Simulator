// 스프링 부트 애플리케이션을 구동하는 진입점 클래스
package com.example.skillsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SkillSimApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkillSimApplication.class, args);
    }
}
