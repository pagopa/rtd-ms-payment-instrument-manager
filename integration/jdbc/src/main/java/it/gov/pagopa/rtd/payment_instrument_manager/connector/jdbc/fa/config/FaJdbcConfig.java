package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.fa.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.jdbc.JdbcProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@PropertySource("classpath:config/faJdbcConfig.properties")
class FaJdbcConfig {

    private final JdbcProperties jdbcProperties;

    @Autowired
    FaJdbcConfig(JdbcProperties jdbcProperties) {
        this.jdbcProperties = jdbcProperties;
    }

    @Bean
    public JdbcTemplate faJdbcTemplate() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(faDataSource());
        JdbcProperties.Template template = jdbcProperties.getTemplate();
        jdbcTemplate.setFetchSize(template.getFetchSize());
        jdbcTemplate.setMaxRows(template.getMaxRows());
        if (template.getQueryTimeout() != null) {
            jdbcTemplate.setQueryTimeout((int) template.getQueryTimeout().getSeconds());
        }
        return jdbcTemplate;
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.fa-datasource.hikari")
    public DataSource faDataSource() {
        final DataSource dataSource = faDataSourceProperties().initializeDataSourceBuilder().build();
        return dataSource;
    }

    @Bean
    @ConfigurationProperties("spring.fa-datasource")
    public DataSourceProperties faDataSourceProperties() {
        return new DataSourceProperties();
    }

}
