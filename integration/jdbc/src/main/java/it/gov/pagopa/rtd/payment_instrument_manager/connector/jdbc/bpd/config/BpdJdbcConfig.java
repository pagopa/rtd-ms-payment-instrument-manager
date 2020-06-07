package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.bpd.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@PropertySource("classpath:config/bpdJdbcConfig.properties")
class BpdJdbcConfig {

    private final JdbcProperties jdbcProperties;

    @Autowired
    BpdJdbcConfig(JdbcProperties jdbcProperties) {
        this.jdbcProperties = jdbcProperties;
    }

    @Bean
    public JdbcTemplate bpdJdbcTemplate() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(bpdDataSource());
        JdbcProperties.Template template = jdbcProperties.getTemplate();
        jdbcTemplate.setFetchSize(template.getFetchSize());
        jdbcTemplate.setMaxRows(template.getMaxRows());
        if (template.getQueryTimeout() != null) {
            jdbcTemplate.setQueryTimeout((int) template.getQueryTimeout().getSeconds());
        }
        return jdbcTemplate;
    }

    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.bpd-datasource.hikari")
    public DataSource bpdDataSource() {
        final DataSource dataSource = bpdDataSourceProperties().initializeDataSourceBuilder().build();
        return dataSource;
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.bpd-datasource")
    public DataSourceProperties bpdDataSourceProperties() {
        return new DataSourceProperties();
    }

}
