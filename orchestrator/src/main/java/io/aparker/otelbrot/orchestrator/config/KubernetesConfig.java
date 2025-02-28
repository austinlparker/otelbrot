package io.aparker.otelbrot.orchestrator.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Kubernetes client
 */
@Configuration
public class KubernetesConfig {
    
    @Value("${kubernetes.namespace:otelbrot}")
    private String namespace;

    /**
     * Creates a KubernetesClient bean
     * 
     * @return The configured KubernetesClient
     */
    @Bean
    public KubernetesClient kubernetesClient() {
        // Use the standard client builder that will automatically detect config
        // from service account or kubeconfig file
        return new KubernetesClientBuilder()
            .build();
    }
}