package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
class PaymentInstrumentManagerDaoImpl implements PaymentInstrumentManagerDao {

    private final JdbcTemplate rtdJdbcTemplate;
    private final JdbcTemplate awardJdbcTemplate;
    private final JdbcTemplate bpdJdbcTemplate;
    private final JdbcTemplate faJdbcTemplate;


    @Autowired
    public PaymentInstrumentManagerDaoImpl(
            @Qualifier("rtdJdbcTemplate") JdbcTemplate rtdJdbcTemplate,
            @Qualifier("awardPeriodJdbcTemplate") JdbcTemplate awardJdbcTemplate,
            @Qualifier("bpdJdbcTemplate") JdbcTemplate bpdJdbcTemplate,
            @Qualifier("faJdbcTemplate") JdbcTemplate faJdbcTemplate) {
        this.rtdJdbcTemplate = rtdJdbcTemplate;
        this.awardJdbcTemplate = awardJdbcTemplate;
        this.bpdJdbcTemplate = bpdJdbcTemplate;
        this.faJdbcTemplate = faJdbcTemplate;
    }

    @Override
    public Map<String,Object> getAwardPeriods() {

        log.info("PaymentInstrumentManagerDaoImpl.getAwardPeriods");

        String queryTemplate = "SELECT min(aw_period_start_d) as start_date, max(aw_period_end_d) as end_date " +
                "FROM bpd_award_period WHERE aw_period_start_d <= current_date AND " +
                "aw_period_end_d >= current_date";

        return awardJdbcTemplate.queryForMap(queryTemplate);
    }

    @Override
    public List<String> getBPDActiveHashPANs(
            String executionDate, String startDate, String endDate, Long offset, Long size) {

        log.info("PaymentInstrumentManagerDaoImpl.getBPDActiveHashPANs offset:"
                + offset + ",size:"+size);

        String queryTemplate = "SELECT bpi.hpan_s" +
                " FROM bpd_payment_instrument.bpd_payment_instrument bpi," +
                " bpd_payment_instrument.bpd_payment_instrument_history bpih " +
                "WHERE bpih. activation_t >= '" + executionDate + "' " +
                " AND (bpih.deactivation_t IS NULL OR bpih.deactivation_t >=  '" + startDate + "')" +
                " AND bpi.hpan_s = bpih.hpan_s " +
                "ORDER BY bpi.insert_date_t ";


        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" OFFSET " + offset + " LIMIT " +size);
        }

        queryTemplate = queryTemplate.concat(") temp_pi ORDER BY temp_pi.insert_date");

        return bpdJdbcTemplate.queryForList(queryTemplate, String.class);

    }

    @Override
    public List<String> getFAActiveHashPANs(String executionDate, Long offset, Long size) {

        log.info("PaymentInstrumentManagerDaoImpl.getFAActiveHashPANs offset:"
                + offset + ",size:"+size);

        String queryTemplate = "select hpan_s as hpan" +
                " from fa_payment_instrument where enabled_b=true AND insert_date_t >= '"
                + executionDate + "'" +
                "order by insert_date_t";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" offset " + offset + " limit " + size);
        }

        return faJdbcTemplate.queryForList(queryTemplate, String.class);

    }

    @Override
    public String getRtdExecutionDate() {

        log.info("PaymentInstrumentManagerDaoImpl.getExecutionData");

        String queryTemplate = "select execution_date_t from rtd_batch_exec_data limit 1";

        return rtdJdbcTemplate.queryForObject(queryTemplate, String.class);

    }

    @Override
    public void insertPaymentInstruments(List<String> paymentInstruments) {

        log.info("PaymentInstrumentManagerDaoImpl.insertPaymentInstruments");

        String queryTemplate = "INSERT INTO rtd_payment_instrument_data(hpan_s) VALUES (?)" +
                " ON CONFLICT DO NOTHING";

        rtdJdbcTemplate.batchUpdate(
                queryTemplate,
                paymentInstruments,
                10000,
                (ps, argument) -> ps.setString(1, argument));

    }

    public List<String> getActiveHashPANs(Long offset, Long size) {

        String queryTemplate = "select * from payment_instrument_hpans order by hpan_s";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" offset " + offset + " limit " + size);
        }

        return rtdJdbcTemplate.queryForList(queryTemplate, String.class);
    }

    public void updateExecutionDate(String executionDate) {

        log.info("PaymentInstrumentManagerDaoImpl.updateExecutionDate");

        String queryTemplate = "UPDATE rtd_batch_exec_data SET execution_date_t='"
                + executionDate +"'";

        rtdJdbcTemplate.update(queryTemplate);
    }


}
