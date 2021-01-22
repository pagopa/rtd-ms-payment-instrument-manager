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

    @Bean(name="bpdDataSourceProperties")
    @ConfigurationProperties("bpd.spring.datasource")
    public DataSourceProperties bpdDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name="bpdDataSource")
    @ConfigurationProperties(prefix = "bpd.spring.datasource.hikari")
    public DataSource bpdDataSource() {
        return bpdDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean("bpdJdbcTemplate")
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
        return faDataSourceProperties().initializeDataSourceBuilder().build();
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
        return awardDataSourceProperties().initializeDataSourceBuilder().build();
    }

    @Bean("awardPeriodJdbcTemplate")
    public JdbcTemplate awardJdbcTemplate() {
        return new JdbcTemplate(awardDataSource());
    }

}
