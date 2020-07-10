package it.gov.pagopa.rtd.payment_instrument_manager.connector.azure_storage.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource("classpath:config/azureStorageConfig.properties")
class AzureStorageConfig {

//    @Bean
//    @Primary
//    @ConfigurationProperties("spring.datasource")
//    public DataSourceProperties dataSourceProperties() {
//        return new DataSourceProperties();
//    }
//
//    @Bean
//    @Primary
//    @ConfigurationProperties(prefix = "spring.datasource.hikari")
//    public DataSource dataSource() {
//        final DataSource dataSource = dataSourceProperties().initializeDataSourceBuilder().build();
//        return dataSource;
//    }

}
