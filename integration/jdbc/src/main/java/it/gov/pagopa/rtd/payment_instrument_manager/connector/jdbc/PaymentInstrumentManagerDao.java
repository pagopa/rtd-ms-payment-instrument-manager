package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import java.util.List;
import java.util.Map;

public interface PaymentInstrumentManagerDao {

    Map<String,Object> getAwardPeriods();

    List<String> getBPDActiveHashPANs(String startDate, String endDate, Long offset, Long size);

    List<String> getFAActiveHashPANs(Long offset, Long size);

}
