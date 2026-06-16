package fpt.swp391.GlucoTrackAlert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableAsync
@EnableCaching
@org.springframework.scheduling.annotation.EnableScheduling
public class GlucoTrackAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlucoTrackAlertApplication.class, args);
    }
}