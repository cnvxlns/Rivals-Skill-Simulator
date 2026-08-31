// 스프링 부트 애플리케이션을 구동하는 진입점 클래스
package com.example.skillsim

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SkillSimApplication

fun main(args: Array<String>) {
    runApplication<SkillSimApplication>(*args)
}
