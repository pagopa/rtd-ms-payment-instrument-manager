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
                "aw_period_end_d + aw_grace_period_n >= current_date";

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
                " WHERE bpih.activation_t >= '" + executionDate + "' " +
                " AND (bpih.deactivation_t IS NULL OR bpih.deactivation_t >=  '" + startDate + "')" +
                " AND bpi.hpan_s = bpih.hpan_s " +
                " ORDER BY bpi.insert_date_t ";


        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" OFFSET " + offset + " LIMIT " +size);
        }

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
    public Map<String, Object> getRtdExecutionDate() {

        log.info("PaymentInstrumentManagerDaoImpl.getExecutionData");

        String queryTemplate = "select bpd_execution_date_t as bpd_exec_date, " +
                "bpd_del_execution_date_t as bpd_del_exec_date, " +
                "fa_del_execution_date_t as fa_del_exec_date, " +
                "fa_execution_date_t as fa_exec_date" +
                " from rtd_batch_exec_data limit 1";

        return rtdJdbcTemplate.queryForMap(queryTemplate);

    }

    @Override
    public void insertBpdPaymentInstruments(List<String> paymentInstruments, int batchSize) {

        log.info("PaymentInstrumentManagerDaoImpl.insertPaymentInstruments");

        String queryTemplate = "INSERT INTO rtd_payment_instrument_data(hpan_s, bpd_enabled_b) VALUES (?,true)" +
                " ON CONFLICT (hpan_s) DO UPDATE SET bpd_enabled_b=true";

        rtdJdbcTemplate.batchUpdate(
                queryTemplate,
                paymentInstruments,
                batchSize,
                (ps, argument) -> ps.setString(1, argument));

    }

    @Override
    public void insertFaPaymentInstruments(List<String> paymentInstruments, int batchSize) {

        log.info("PaymentInstrumentManagerDaoImpl.insertPaymentInstruments");

        String queryTemplate = "INSERT INTO rtd_payment_instrument_data(hpan_s, fa_enabled_b) VALUES (?,true)" +
                " ON CONFLICT (hpan_s) DO UPDATE SET fa_enabled_b=true";

        rtdJdbcTemplate.batchUpdate(
                queryTemplate,
                paymentInstruments,
                batchSize,
                (ps, argument) -> ps.setString(1, argument));

    }

    @Override
    public List<String> getActiveHashPANs(Long offset, Long size) {

        String queryTemplate = "select * from rtd_payment_instrument_data order by hpan_s" +
                " WHERE bpd_enabled_b=true OR fa_enabled_b=true";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" offset " + offset + " limit " + size);
        }

        return rtdJdbcTemplate.queryForList(queryTemplate, String.class);
    }

    @Override
    public void updateExecutionDate(String executionDate) {

        log.info("PaymentInstrumentManagerDaoImpl.updateExecutionDate");

        String queryTemplate = "UPDATE rtd_batch_exec_data SET bpd_execution_date_t='"
                + executionDate + "', bpd_del_execution_date_t='" + executionDate
                + "', fa_execution_date_t='" + executionDate +
                "', fa_del_execution_date_t='"+ executionDate + "'";

        rtdJdbcTemplate.update(queryTemplate);
    }

    @Override
    public List<String> getBpdDisabledPans(String executionDate, String startDate, Long offset, Long size) {

        log.info("PaymentInstrumentManagerDaoImpl.getBpdDisabledPans offset:"
                + offset + ",size:"+size);

        String queryTemplate = "SELECT bpi.hpan_s FROM " +
                "bpd_payment_instrument.bpd_payment_instrument bpi " +
                "WHERE bpi.status_c = 'INACTIVE' " +
                "AND cancellation_t < '" + startDate + "' " +
                "ORDER BY bpi.insert_date_t";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" offset " + offset + " limit " + size);
        }

        return bpdJdbcTemplate.queryForList(queryTemplate, String.class);

    }

    @Override
    public List<String> getBpdDisabledCitizenPans(String executionDate, String startDate, Long offset, Long size) {

        log.info("PaymentInstrumentManagerDaoImpl.getBpdDisabledPans offset:"
                + offset + ",size:"+size);

        String queryTemplate = "SELECT bpi.hpan_s FROM dblink('bpd_citizen_remote'," +
                "'SELECT fiscal_code_s FROM bpd_citizen.bpd_citizen WHERE enabled_b=false AND" +
                " cancellation_t >= ''" + executionDate + "'' ') AS bpc(fiscal_code_s varchar)," +
                " bpd_payment_instrument.bpd_payment_instrument bpi" +
                " WHERE bpi.fiscal_code_s = bpc.fiscal_code_s" +
                " ORDER BY bpi.insert_date_t";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" offset " + offset + " limit " + size);
        }

        return bpdJdbcTemplate.queryForList(queryTemplate, String.class);

    }

    @Override
    public List<String> getFaDisabledPans(String executionDate, Long offset, Long size) {

        log.info("PaymentInstrumentManagerDaoImpl.getFaDisabledPans offset:"
                + offset + ",size:"+size);

        String queryTemplate = "select hpan_s as hpan" +
                " from fa_payment_instrument where enabled_b=false AND cancellation_t >= '"
                + executionDate + "'" +
                "order by insert_date_t";

        if (offset != null && size != null) {
            queryTemplate = queryTemplate.concat(" offset " + offset + " limit " + size);
        }

        return faJdbcTemplate.queryForList(queryTemplate, String.class);

    }


    @Override
    public void disableBpdPaymentInstruments(List<String> paymentInstruments, int batchSize) {

        log.info("PaymentInstrumentManagerDaoImpl.deleteBpdPaymentInstruments");

        String queryTemplate = "UPDATE rtd_payment_instrument_data SET bpd_enabled_b=false WHERE hpan_s=?";

        rtdJdbcTemplate.batchUpdate(
                queryTemplate,
                paymentInstruments,
                batchSize,
                (ps, argument) -> ps.setString(1, argument));

    }

    @Override
    public void disableFaPaymentInstruments(List<String> paymentInstruments, int batchSize) {

        log.info("PaymentInstrumentManagerDaoImpl.deleteFaPaymentInstruments");

        String queryTemplate = "UPDATE rtd_payment_instrument_data SET fa_enabled_b=false WHERE hpan_s=?";

        rtdJdbcTemplate.batchUpdate(
                queryTemplate,
                paymentInstruments,
                batchSize,
                (ps, argument) -> ps.setString(1, argument));

    }

}
