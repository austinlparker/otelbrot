package io.aparker.otelbrot.orchestrator;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * This test is currently disabled because it requires a full servlet container
 * for WebSocket support. In a real environment, integration tests would run
 * in a proper container environment.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(properties = {
    "spring.main.allow-bean-definition-overriding=true",
    "websocket.enabled=false"
})
class OrchestratorApplicationTests {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public KubernetesClient kubernetesClient() {
            KubernetesMockServer server = new KubernetesMockServer();
            server.init();
            return server.createClient();
        }

        @Bean
        @Primary
        public OpenTelemetry openTelemetry() {
            return OpenTelemetry.noop();
        }
    }

	@Test
	@Disabled("Requires full servlet container for WebSocket support")
	void contextLoads() {
		// Just verify that the application context loads successfully
	}
}
