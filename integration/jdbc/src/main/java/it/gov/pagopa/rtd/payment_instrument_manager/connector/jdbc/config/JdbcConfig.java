package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@PropertySource("classpath:config/jdbcConfig.properties")
class JdbcConfig {

    @Bean(name="rtdDataSourceProperties")
    @Primary
    @ConfigurationProperties("rtd.spring.datasource")
    public DataSourceProperties rtdDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name="rtdDataSource")
    @Primary
    @ConfigurationProperties(prefix = "rtd.spring.datasource.hikari")
    public DataSource rtdDataSource() {
        return rtdDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean("rtdJdbcTemplate")
    @Primary
    public JdbcTemplate rtdJdbcTemplate() {
        return new JdbcTemplate(rtdDataSource());
    }

}
