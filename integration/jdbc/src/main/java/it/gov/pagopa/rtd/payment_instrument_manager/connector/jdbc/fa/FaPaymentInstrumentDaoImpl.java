package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.fa;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class FaPaymentInstrumentDaoImpl implements FaPaymentInstrumentDao {

    @Autowired
    @Qualifier("faJdbcTemplate")
    private JdbcTemplate faJdbcTemplate;

    public List<String> getActiveHashPANs() {
        final List<String> hpans = faJdbcTemplate.queryForList("select hpan_s from fa_payment_instrument where enabled_b = true", String.class);
        return hpans;
    }

}
