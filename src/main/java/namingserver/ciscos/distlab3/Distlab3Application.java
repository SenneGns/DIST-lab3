package namingserver.ciscos.distlab3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
    "namingserver.ciscos.distlab3",
    "discovery.ciscos.distlab4"
})
public class Distlab3Application {
    public static void main(String[] args) {
        SpringApplication.run(Distlab3Application.class, args);
    }
}
