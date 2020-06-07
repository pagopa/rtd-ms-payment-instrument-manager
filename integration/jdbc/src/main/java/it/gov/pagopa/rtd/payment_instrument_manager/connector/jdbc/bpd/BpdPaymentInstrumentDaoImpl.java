package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.bpd;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class BpdPaymentInstrumentDaoImpl implements BpdPaymentInstrumentDao {

    @Autowired
    @Qualifier("bpdJdbcTemplate")
    private JdbcTemplate bpdJdbcTemplate;

    public List<String> getActivePaymentInstruments() {
        final List<String> hpans = bpdJdbcTemplate.queryForList("select hpan_s from bpd_payment_instrument where enabled_b = true", String.class);
        return hpans;
    }

}
