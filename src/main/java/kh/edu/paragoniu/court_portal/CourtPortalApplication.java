package kh.edu.paragoniu.court_portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@EntityScan(basePackages = { "kh.edu.paragoniu.court_shared.entity" })
@EnableJpaRepositories(
	basePackages = { "kh.edu.paragoniu.court_shared.repository" }
)
public class CourtPortalApplication {

	public static void main(String[] args) {
		SpringApplication.run(CourtPortalApplication.class, args);
	}

}
