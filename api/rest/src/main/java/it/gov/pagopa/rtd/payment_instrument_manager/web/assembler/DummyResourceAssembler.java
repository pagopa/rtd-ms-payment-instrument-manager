package it.gov.pagopa.rtd.payment_instrument_manager.web.assembler;

import it.gov.pagopa.rtd.payment_instrument_manager.web.model.DummyResource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
//FIXME: remove me (created as archetype test)
public class DummyResourceAssembler {

    public DummyResource toResource(Object model) {
        DummyResource resource = null;

        if (model != null) {
            resource = new DummyResource();
            BeanUtils.copyProperties(model, resource);
        }

        return resource;
    }

}
