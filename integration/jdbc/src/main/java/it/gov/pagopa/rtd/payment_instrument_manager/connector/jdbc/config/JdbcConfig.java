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

    @Bean(name="bpdDataSourceProperties")
    @Primary
    @ConfigurationProperties("bpd.spring.datasource")
    public DataSourceProperties bpdDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name="bpdDataSource")
    @Primary
    @ConfigurationProperties(prefix = "bpd.spring.datasource.hikari")
    public DataSource bpdDataSource() {
        final DataSource dataSource = bpdDataSourceProperties().initializeDataSourceBuilder().build();
        return dataSource;
    }

    @Bean("bpdJdbcTemplate")
    @Primary
    public JdbcTemplate bpdJdbcTemplate() {
        return new JdbcTemplate(bpdDataSource());
    }

    @Bean(name="faDataSourceProperties")
    @ConfigurationProperties("fa.spring.datasource")
    public DataSourceProperties faDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name="faDataSource")
    @ConfigurationProperties(prefix = "fa.spring.datasource.hikari")
    public DataSource faDataSource() {
        final DataSource dataSource = faDataSourceProperties().initializeDataSourceBuilder().build();
        return dataSource;
    }

    @Bean("faJdbcTemplate")
    public JdbcTemplate faJdbcTemplate() {
        return new JdbcTemplate(faDataSource());
    }

    @Bean(name="awardPeriodDataSourceProperties")
    @ConfigurationProperties("award.spring.datasource")
    public DataSourceProperties awardDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name="awardPeriodDataSource")
    @ConfigurationProperties(prefix = "award.spring.datasource.hikari")
    public DataSource awardDataSource() {
        final DataSource dataSource = awardDataSourceProperties().initializeDataSourceBuilder().build();
        return dataSource;
    }

    @Bean("awardPeriodJdbcTemplate")
    public JdbcTemplate awardJdbcTemplate() {
        return new JdbcTemplate(awardDataSource());
    }

}
