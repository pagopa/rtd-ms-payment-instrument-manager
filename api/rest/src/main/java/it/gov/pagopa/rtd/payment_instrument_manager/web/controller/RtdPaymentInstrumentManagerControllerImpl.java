package it.gov.pagopa.rtd.payment_instrument_manager.web.controller;

import eu.sia.meda.core.controller.StatelessController;
import it.gov.pagopa.rtd.payment_instrument_manager.service.PaymentInstrumentManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

@RestController
@Slf4j
class RtdPaymentInstrumentManagerControllerImpl extends StatelessController implements RtdPaymentInstrumentManagerController {

    private final PaymentInstrumentManagerService paymentInstrumentManagerService;

    @Autowired
    public RtdPaymentInstrumentManagerControllerImpl(PaymentInstrumentManagerService paymentInstrumentManagerService) {
        this.paymentInstrumentManagerService = paymentInstrumentManagerService;
    }

    @Override
    public void getHashedPans(HttpServletResponse httpServletResponse,
                              String filePartId) {
        if (log.isDebugEnabled()) {
            log.debug("RtdPaymentInstrumentManagerControllerImpl.getHashedPans");
        }
        final String downloadLink = paymentInstrumentManagerService.getDownloadLink(filePartId);
        if (log.isDebugEnabled()) {
            log.debug("downloadLink = " + downloadLink);
        }
        httpServletResponse.setStatus(HttpServletResponse.SC_FOUND);
        httpServletResponse.setHeader("Location", downloadLink);
    }

    @Override
    public void uploadActiveHashPANs() {
        paymentInstrumentManagerService.generateFileForAcquirer();
    }

    @Override
    public void refreshActiveHpans() {
        paymentInstrumentManagerService.refreshActiveHpans();
    }
}
