package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import java.util.List;
import java.util.Map;

public interface PaymentInstrumentManagerDao {

    Map<String,Object> getAwardPeriods();

    List<Map<String,Object>> getBPDActiveHashPANs(
            String startDate, String endDate, String executionDate, Long offset, Long size);

    List<Map<String,Object>> getFAActiveHashPANs(String executionDate, Long offset, Long size);

    String getRtdExecutionDate();

    void insertPaymentInstruments(List<Map<String, Object>> paymentInstruments);
}
