package kr.co.dataric;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // scheduling 위한 적용
@SpringBootApplication
public class KafkaConsumerApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(KafkaConsumerApplication.class);
	}
}
