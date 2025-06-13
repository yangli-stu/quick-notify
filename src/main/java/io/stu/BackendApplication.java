package io.stu;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Slf4j
@EntityScan(basePackages = {"io.stu.**.model"})
@EnableJpaRepositories(basePackages = {"io.stu.**.repository"})
@EnableJpaAuditing
@EnableCaching
@SpringBootApplication
public class BackendApplication {

    // 固定以dev环境启动
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(BackendApplication.class);
        app.setAdditionalProfiles("dev");
        app.run(args);
    }

}
