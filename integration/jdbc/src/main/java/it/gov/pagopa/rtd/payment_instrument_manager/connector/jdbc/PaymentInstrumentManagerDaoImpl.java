package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@Slf4j
class PaymentInstrumentManagerDaoImpl implements PaymentInstrumentManagerDao {


    private final JdbcTemplate bpdJdbcTemplate;
    private final JdbcTemplate faJdbcTemplate;


    @Autowired
    public PaymentInstrumentManagerDaoImpl(
            @Qualifier("bpdJdbcTemplate") JdbcTemplate bpdJdbcTemplate,
            @Qualifier("faJdbcTemplate") JdbcTemplate faJdbcTemplate) {
        this.bpdJdbcTemplate = bpdJdbcTemplate;
        this.faJdbcTemplate = faJdbcTemplate;
    }

    public List<String> getBPDActiveHashPANs(Long offset, Long size) {

        log.info("PaymentInstrumentManagerDaoImpl.getBPDActiveHashPANs offset:"
                + offset + ",size:"+size);

        String queryTemplate = "select hpan_s from bpd_payment_instrument order by insert_date_t";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" offset " + offset + " limit " + size);
        }

        return bpdJdbcTemplate.queryForList(queryTemplate, String.class);
    }

    public List<String> getFAActiveHashPANs(Long offset, Long size) {

        log.info("PaymentInstrumentManagerDaoImpl.getFAActiveHashPANs offset:"
                + offset + ",size:"+size);

        String queryTemplate = "select hpan_s from fa_payment_instrument order by insert_date_t";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" offset " + offset + " limit " + size);
        }

        return faJdbcTemplate.queryForList(queryTemplate, String.class);
    }



}
