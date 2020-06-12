package it.gov.pagopa.rtd.payment_instrument_manager.web.controller;

import com.microsoft.azure.storage.StorageException;
import eu.sia.meda.core.controller.StatelessController;
import it.gov.pagopa.rtd.payment_instrument_manager.service.PaymentInstrumentManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;

@RestController
@Slf4j
class RtdPaymentInstrumentManagerControllerImpl extends StatelessController implements RtdPaymentInstrumentManagerController {

    private final PaymentInstrumentManager paymentInstrumentManager;

    @Autowired
    public RtdPaymentInstrumentManagerControllerImpl(PaymentInstrumentManager paymentInstrumentManager) {
        this.paymentInstrumentManager = paymentInstrumentManager;
    }

    @Override
    public void getHashPANs(HttpServletResponse httpServletResponse) throws InvalidKeyException, StorageException, URISyntaxException {
        final String downloadLink = paymentInstrumentManager.getDownloadLink();
        httpServletResponse.setStatus(HttpServletResponse.SC_FOUND);
        httpServletResponse.setHeader("Location", downloadLink);
    }

}
