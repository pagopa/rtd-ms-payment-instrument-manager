package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import java.util.List;
import java.util.Map;

public interface PaymentInstrumentManagerDao {

    Map<String,Object> getAwardPeriods();

    List<String> getBPDActiveHashPANs(
            String startDate, String endDate, String executionDate, Long offset, Long size);

    List<String> getFAActiveHashPANs(String executionDate, Long offset, Long size);

    String getRtdExecutionDate();

    void insertPaymentInstruments(List<String> paymentInstruments);

    List<String> getActiveHashPANs(Long offset, Long size);

    void updateExecutionDate(String executionDate);

}
