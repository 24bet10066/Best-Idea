package com.partlinq.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for the PartLinQ application.
 * Provides API documentation metadata and configuration.
 */
@Configuration
public class OpenApiConfig {

	/**
	 * Configure OpenAPI specification
	 */
	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("PartLinQ API")
				.version("1.0.0")
				.description("""
					PartLinQ - A Trust-Graph Powered Technician-Parts Ecosystem

					This API provides endpoints for managing technicians, parts shops, spare parts,
					orders, and trust scores in the PartLinQ platform.
					""")
				.contact(new Contact()
					.name("PartLinQ Support")
					.email("support@partlinq.com")
					.url("https://partlinq.com"))
				.license(new License()
					.name("Apache 2.0")
					.url("https://www.apache.org/licenses/LICENSE-2.0.html")))
			.servers(List.of(
				new Server()
					.url("http://localhost:8080/api")
					.description("Development Server"),
				new Server()
					.url("https://api.partlinq.com/api")
					.description("Production Server")
			));
	}

}
