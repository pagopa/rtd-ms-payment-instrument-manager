package it.gov.pagopa.rtd.payment_instrument_manager.service;


import java.util.Set;

public interface PaymentInstrumentManager {

    Set<String> getActivePaymentInstruments();

}
