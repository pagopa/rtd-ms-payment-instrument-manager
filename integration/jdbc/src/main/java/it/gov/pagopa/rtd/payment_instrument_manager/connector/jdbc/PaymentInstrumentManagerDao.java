package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc;

import java.util.List;
import java.util.Map;

public interface PaymentInstrumentManagerDao {

    List<String> getActiveHashPANs(Long offset, Long size);

    void refreshView();

}
