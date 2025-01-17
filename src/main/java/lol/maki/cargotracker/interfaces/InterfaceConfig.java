package lol.maki.cargotracker.interfaces;

import am.ik.accesslogger.AccessLogger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InterfaceConfig {

	@Bean
	public AccessLogger accessLogger() {
		return new AccessLogger(httpExchange -> {
			final String uri = httpExchange.getRequest().getUri().getPath();
			return uri != null && !(uri.equals("/readyz") || uri.equals("/livez") || uri.startsWith("/actuator"));
		});
	}

}
