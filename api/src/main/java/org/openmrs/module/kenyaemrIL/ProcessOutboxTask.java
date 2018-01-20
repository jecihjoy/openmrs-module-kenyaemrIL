package org.openmrs.module.kenyaemrIL;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import org.openmrs.api.context.Context;
import org.openmrs.module.kenyaemrIL.api.KenyaEMRILService;
import org.openmrs.module.kenyaemrIL.il.KenyaEMRILMessage;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

/**
 * Implementation of a task that processes the IL inbox every one minute .
 */
public class ProcessOutboxTask extends AbstractTask {
    private final String IL_URL = "http://52.178.24.227:9721/api/";


    // Logger
    private static final Logger log = LoggerFactory.getLogger(ProcessOutboxTask.class);


    /**
     * @see AbstractTask#execute()
     */
    @Override
    public void execute() {
        log.info("Executing task at " + new Date());
//        Fetch non-processed inbox messages
        List<KenyaEMRILMessage> pendingOutboxes = fetchILOutboxes(false);
        for (KenyaEMRILMessage pendingOutbox : pendingOutboxes) {
            processFetchedRecord(pendingOutbox);
        }
    }

    private void processFetchedRecord(KenyaEMRILMessage outbox) {
//        Send to IL and mark as sent
        Client restClient = Client.create();
        WebResource webResource = restClient.resource(IL_URL);
        ClientResponse resp = webResource.type("application/json")
                .post(ClientResponse.class, outbox.getMessage());

        System.out.println("The status received from the server: "+resp.getStatus());
        if (resp.getStatus() != 200) {
            System.err.println("Unable to connect to the server");
        } else {
            outbox.setRetired(true);
            getEMRILService().saveKenyaEMRILMessage(outbox);
        }

    }

    private List<KenyaEMRILMessage> fetchILOutboxes(boolean fetchRetired) {
        return getEMRILService().getKenyaEMRILOutboxes(fetchRetired);
    }

    private KenyaEMRILService getEMRILService() {
        return Context.getService(KenyaEMRILService.class);
    }

}