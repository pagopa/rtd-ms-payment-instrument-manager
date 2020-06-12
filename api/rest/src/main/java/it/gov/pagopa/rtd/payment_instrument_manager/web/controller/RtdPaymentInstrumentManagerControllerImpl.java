package it.gov.pagopa.rtd.payment_instrument_manager.web.controller;

import eu.sia.meda.core.controller.StatelessController;
import it.gov.pagopa.rtd.payment_instrument_manager.connector.jdbc.bpd.BpdPaymentInstrumentDao;
import it.gov.pagopa.rtd.payment_instrument_manager.web.assembler.DummyResourceAssembler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Slf4j
class RtdPaymentInstrumentManagerControllerImpl extends StatelessController implements RtdPaymentInstrumentManagerController {

    private final BeanFactory beanFactory;
    private final DummyResourceAssembler dummyResourceAssembler;

    @Autowired
    private BpdPaymentInstrumentDao bpdPaymentInstrumentDao;

    @Autowired
    public RtdPaymentInstrumentManagerControllerImpl(BeanFactory beanFactory, DummyResourceAssembler dummyResourceAssembler) {
        this.beanFactory = beanFactory;
        this.dummyResourceAssembler = dummyResourceAssembler;
    }

    @Override
    public List<String> test() { //FIXME: remove me (created as archetype test)

        final List<String> test = bpdPaymentInstrumentDao.getActiveHashPANs();

        return test;
    }

}
