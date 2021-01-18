package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class PaymentInstrumentManagerDaoImpl implements PaymentInstrumentManagerDao {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PaymentInstrumentManagerDaoImpl(@Qualifier("rtdJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> getActiveHashPANs(Long offset, Long size) {

        String queryTemplate = "select * from payment_instrument_hpans order by hpan_s";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" offset " + offset + " limit " + size);
        }

        return jdbcTemplate.queryForList(queryTemplate, String.class);
    }

    @Override
    public void refreshView() {
        jdbcTemplate.execute("refresh materialized view payment_instrument_hpans");
    }

}
