package it.gov.pagopa.rtd.payment_instrument_manager.web.controller;

import com.microsoft.azure.storage.StorageException;
import eu.sia.meda.core.controller.StatelessController;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.PaymentInstrumentManagerDao;
import it.gov.pagopa.rtd.payment_instrument_manager.service.PaymentInstrumentManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.List;

@RestController
@Slf4j
class RtdPaymentInstrumentManagerControllerImpl extends StatelessController implements RtdPaymentInstrumentManagerController {

    private final PaymentInstrumentManagerService paymentInstrumentManagerService;
    @Autowired
    private PaymentInstrumentManagerDao paymentInstrumentManagerDao;

    @Autowired
    public RtdPaymentInstrumentManagerControllerImpl(PaymentInstrumentManagerService paymentInstrumentManagerService) {
        this.paymentInstrumentManagerService = paymentInstrumentManagerService;
    }


    @Override
    public void getHashedPans(HttpServletResponse httpServletResponse) throws InvalidKeyException, StorageException, URISyntaxException {
        if (log.isDebugEnabled()) {
            log.debug("RtdPaymentInstrumentManagerControllerImpl.getHashedPans");
        }
        final String downloadLink = paymentInstrumentManagerService.getDownloadLink();
        if (log.isDebugEnabled()) {
            log.debug("downloadLink = " + downloadLink);
        }
        System.out.println("downloadLink = " + downloadLink);
        httpServletResponse.setStatus(HttpServletResponse.SC_FOUND);
        httpServletResponse.setHeader("Location", downloadLink);
    }


    @Override
    public List<String> getActiveHashPANs() {
        return paymentInstrumentManagerDao.getActiveHashPANs();
    }


    @Override
    public void uploadActiveHashPANs() {
        paymentInstrumentManagerService.generateFileForAcquirer();
    }
}
