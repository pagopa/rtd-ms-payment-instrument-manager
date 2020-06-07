package it.gov.pagopa.rtd.payment_instrument_manager.command;


import eu.sia.meda.core.command.BaseCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

//FIXME: remove me (created as archetype test)
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
class DummyCommandImpl extends BaseCommand<Object> implements DummyCommand {

    private final String message;

    public DummyCommandImpl(String message) {
        this.message = message;
    }

    @Override
    public Object doExecute() {
        log.info(message);
        return new Object();
    }

}
