package io.github.yienruuuuu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"io.github.yienruuuuu"})
public class Application {

    /**
     * 啟動 Spring Boot 應用。
     *
     * @param args 啟動參數
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
