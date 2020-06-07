package it.gov.pagopa.rtd.payment_instrument_manager.service;

import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.bpd.BpdPaymentInstrumentDao;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.fa.FaPaymentInstrumentDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
class PaymentInstrumentManagerImpl implements PaymentInstrumentManager {

    private final BpdPaymentInstrumentDao bpdPaymentInstrumentDao;
    private final FaPaymentInstrumentDao faPaymentInstrumentDao;

    @Autowired
    public PaymentInstrumentManagerImpl(BpdPaymentInstrumentDao bpdPaymentInstrumentDao, FaPaymentInstrumentDao faPaymentInstrumentDao) {
        this.bpdPaymentInstrumentDao = bpdPaymentInstrumentDao;
        this.faPaymentInstrumentDao = faPaymentInstrumentDao;
    }

    @Scheduled(cron = "${batchConfiguration.paymentInstrumentsForAcquirer.cron}")
    public void generateFileForAcquirer() {
        final Set<String> activePaymentInstruments = getActivePaymentInstruments();
        //TODO: create a temp csv file, then upload it to Azure File Storage as BLOB container
    }

    @Override
    public Set<String> getActivePaymentInstruments() {
        final Set<String> result = new HashSet<>();

        final List<String> bpdActivePaymentInstruments = bpdPaymentInstrumentDao.getActivePaymentInstruments();
        result.addAll(bpdActivePaymentInstruments);

        final List<String> faActivePaymentInstruments = faPaymentInstrumentDao.getActivePaymentInstruments();
        result.addAll(faActivePaymentInstruments);

        return result;
    }

}
