package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class PaymentInstrumentManagerDaoImpl implements PaymentInstrumentManagerDao {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public PaymentInstrumentManagerDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> getActiveHashPANs() {
        return jdbcTemplate.queryForList("select get_payment_instrument_hpans()", String.class);
    }

}
