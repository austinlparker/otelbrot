package io.aparker.otelbrot.worker;

import io.aparker.otelbrot.worker.config.TestConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {TestConfig.class})
@TestPropertySource(properties = {
    "spring.main.allow-bean-definition-overriding=true"
})
@Disabled("Tests are disabled until environment setup fixed")
class WorkerApplicationTests {

	@Test
	void contextLoads() {
		// Just verify that the application context loads successfully
	}

}
