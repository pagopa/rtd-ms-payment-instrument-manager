package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
class PaymentInstrumentManagerDaoImpl implements PaymentInstrumentManagerDao {


    private final JdbcTemplate awardJdbcTemplate;
    private final JdbcTemplate bpdJdbcTemplate;
    private final JdbcTemplate faJdbcTemplate;


    @Autowired
    public PaymentInstrumentManagerDaoImpl(
            @Qualifier("awardPeriodJdbcTemplate") JdbcTemplate awardJdbcTemplate,
            @Qualifier("bpdJdbcTemplate") JdbcTemplate bpdJdbcTemplate,
            @Qualifier("faJdbcTemplate") JdbcTemplate faJdbcTemplate) {
        this.awardJdbcTemplate = awardJdbcTemplate;
        this.bpdJdbcTemplate = bpdJdbcTemplate;
        this.faJdbcTemplate = faJdbcTemplate;
    }

    @Override
    public Map<String,Object> getAwardPeriods() {

        log.info("PaymentInstrumentManagerDaoImpl.getAwardPeriods");

        String queryTemplate = "SELECT min(aw_period_start_d) as start_date, max(aw_period_end_d) as end_date " +
                "FROM bpd_award_period.bpd_award_period WHERE aw_period_start_d <= current_date AND " +
                "aw_period_end_d >= current_date";

        return awardJdbcTemplate.queryForMap(queryTemplate);
    }

    public List<String> getBPDActiveHashPANs(String startDate, String endDate, Long offset, Long size) {

        log.info("PaymentInstrumentManagerDaoImpl.getBPDActiveHashPANs offset:"
                + offset + ",size:"+size);

        String queryTemplate = "SELECT temp_pi.hpan_s FROM " +
                "(SELECT DISTINCT bpi.hpan_s, bpi.insert_date_t " +
                "FROM bpd_payment_instrument.bpd_payment_instrument_history bpi " +
                "WHERE activation_t <= '" + startDate +
                "' AND (deactivation_t IS NULL OR deactivation_t >= '" + endDate + "')" +
                " ORDER BY bpi.insert_date_t";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" OFFSET " + offset + "LIMIT " +size);
        }

        queryTemplate = queryTemplate.concat(") temp_pi");

        return bpdJdbcTemplate.queryForList(queryTemplate, String.class);

    }

    public List<String> getFAActiveHashPANs(Long offset, Long size) {

        log.info("PaymentInstrumentManagerDaoImpl.getFAActiveHashPANs offset:"
                + offset + ",size:"+size);

        String queryTemplate = "select hpan_s from fa_payment_instrument where enabled_b=true " +
                "order by insert_date_t";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" offset " + offset + " limit " + size);
        }

        return faJdbcTemplate.queryForList(queryTemplate, String.class);

    }



}
