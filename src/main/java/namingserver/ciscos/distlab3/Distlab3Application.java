package namingserver.ciscos.distlab3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = { //fixt dat het de andere package ook runt
		"namingserver.ciscos.distlab3",
		"discovery.ciscos.distlab4"
})
@EnableScheduling
public class Distlab3Application {

	public static void main(String[] args) {
		SpringApplication.run(Distlab3Application.class, args);
	}

}
