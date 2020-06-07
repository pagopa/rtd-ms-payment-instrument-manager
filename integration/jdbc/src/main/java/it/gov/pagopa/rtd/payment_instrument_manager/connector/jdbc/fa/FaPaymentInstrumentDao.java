package it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.fa;

import java.util.List;

public interface FaPaymentInstrumentDao {

    List<String> getActivePaymentInstruments();

}
