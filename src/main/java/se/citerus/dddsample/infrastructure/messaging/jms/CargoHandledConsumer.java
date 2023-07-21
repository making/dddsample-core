package se.citerus.dddsample.infrastructure.messaging.jms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import se.citerus.dddsample.application.CargoInspectionService;
import se.citerus.dddsample.domain.model.cargo.TrackingId;

import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import java.lang.invoke.MethodHandles;

/**
 * Consumes JMS messages and delegates notification of misdirected
 * cargo to the tracking service.
 * <p>
 * This is a programmatic hook into the JMS infrastructure to
 * make cargo inspection message-driven.
 */

@Component
public class CargoHandledConsumer {

    private final CargoInspectionService cargoInspectionService;
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public CargoHandledConsumer(CargoInspectionService cargoInspectionService) {
        this.cargoInspectionService = cargoInspectionService;
    }

    @JmsListener(destination = "cargoHandledQueue")
    public void onMessage(final Message message) {
        try {
            final TextMessage textMessage = (TextMessage) message;
            final String trackingidString = textMessage.getText();

            cargoInspectionService.inspectCargo(new TrackingId(trackingidString));
        } catch (Exception e) {
            logger.error("Error consuming CargoHandled message", e);
        }
    }
}
