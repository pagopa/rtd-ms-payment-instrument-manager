package it.gov.pagopa.rtd.payment_instrument_manager.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:config/batchAcquirer.properties")
class BatchConfig {
}
