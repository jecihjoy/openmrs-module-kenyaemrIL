package org.openmrs.module.kenyaemrIL.fragment.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import com.google.common.base.Strings;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.openmrs.GlobalProperty;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.kenyaemr.nupi.UpiUtilsDataExchange;
import org.openmrs.module.kenyaemrIL.api.KenyaEMRILService;
import org.openmrs.module.kenyaemrIL.api.shr.FhirConfig;
import org.openmrs.module.kenyaemrIL.programEnrollment.ExpectedTransferInPatients;
import org.openmrs.module.kenyaemrIL.util.ILUtils;
import org.openmrs.module.metadatadeploy.MetadataUtils;
import org.openmrs.ui.framework.SimpleObject;
import org.openmrs.ui.framework.annotation.FragmentParam;
import org.openmrs.ui.framework.fragment.FragmentModel;
import org.openmrs.util.PrivilegeConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestParam;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * controller for pivotTableCharts fragment
 */
public class ReferralsDataExchangeFragmentController {
    // Logger
    private static final Logger log = LoggerFactory.getLogger(ReferralsDataExchangeFragmentController.class);
    public static final String OPENMRS_ID = "dfacd928-0370-4315-99d7-6ec1c9f7ae76";
    public static final String NUPI = "f85081e2-b4be-4e48-b3a4-7994b69bb101";

    @Qualifier("fhirR4")
    private FhirContext fhirContext;

    public FhirContext getFhirContext() {
        return fhirContext;
    }

    public IGenericClient getFhirClient() throws Exception {
        IGenericClient fhirClient = fhirContext.newRestfulGenericClient(ILUtils.getShrServerUrl());
        return fhirClient;
    }

    public void controller(FragmentModel model, @FragmentParam("patient") Patient patient) {

    }

    /**
     * Get community referrals for FHIR server       *
     *
     * @return
     */
    public SimpleObject pullCommunityReferralsFromFhir() throws Exception {
        System.out.println("Fhir :Start pullCommunityReferralsFromFhir ==>");
        org.hl7.fhir.r4.model.Resource fhirServiceRequestResource;
        org.hl7.fhir.r4.model.ServiceRequest fhirServiceRequest = null;
        FhirConfig fhirConfig = Context.getRegisteredComponents(FhirConfig.class).get(0);
        Bundle serviceRequestResourceBundle = fhirConfig.fetchReferrals();
        System.out.println(fhirConfig.getFhirContext().newJsonParser().encodeResourceToString(serviceRequestResourceBundle));
        if (serviceRequestResourceBundle != null && !serviceRequestResourceBundle.getEntry().isEmpty()) {
            for (int i = 0; i < serviceRequestResourceBundle.getEntry().size(); i++) {
                fhirServiceRequestResource = serviceRequestResourceBundle.getEntry().get(i).getResource();
                System.out.println("Fhir : Checking Service request is null ==>");
                if (fhirServiceRequestResource != null) {
                    System.out.println("Fhir : Service request is not null ==>");
                    fhirServiceRequest = (org.hl7.fhir.r4.model.ServiceRequest) fhirServiceRequestResource;
                    String nupiNumber = fhirServiceRequest.getSubject().getIdentifier().getValue();
                    if (Strings.isNullOrEmpty(nupiNumber)) {
                        continue;
                    }
                    System.out.println("NUPI :  ==>" + nupiNumber);
                    if (nupiNumber != null) {
                        String serverUrl = "https://dhpstagingapi.health.go.ke/partners/registry/search/upi/" + nupiNumber;

                        persistReferralData(getCRPatient(serverUrl), fhirConfig, fhirServiceRequest);
                    }
                } else {
                    System.out.println("Fhir : Service request is null ==>");
                }
            }
        }
        return SimpleObject.create("success", "");
    }

    public SimpleObject updateShrReferral(@RequestParam("patientId") Integer referral) throws Exception {
        FhirConfig fhirConfig = Context.getRegisteredComponents(FhirConfig.class).get(0);

        KenyaEMRILService service = Context.getService(KenyaEMRILService.class);
        ExpectedTransferInPatients referred = service.getCommunityReferralsById(referral);
        PersonAttribute referralStatusAttribute = new PersonAttribute();
        PersonAttributeType referralStatusAttributeType = Context.getPersonService().getPersonAttributeTypeByUuid("df7e9996-23b5-4f66-a799-97498d19850d");
        if (referralStatusAttributeType != null) {
            referralStatusAttribute.setAttributeType(referralStatusAttributeType);
            referralStatusAttribute.setValue("Completed");
            referred.getPatient().addAttribute(referralStatusAttribute);
            Context.getPatientService().savePatient(referred.getPatient());
        }

        IParser parser = fhirConfig.getFhirContext().newJsonParser().setPrettyPrint(true);
        ServiceRequest serviceRequest = parser.parseResource(ServiceRequest.class, referred.getPatientSummary());
        System.out.println(serviceRequest.getStatus());
        System.out.println(fhirConfig.getFhirContext().newJsonParser().encodeResourceToString(serviceRequest));
        serviceRequest.setStatus(ServiceRequest.ServiceRequestStatus.COMPLETED);
        postFhirResource(fhirConfig.getFhirContext().newJsonParser().encodeResourceToString(serviceRequest),"https://interoperabilitylab.uonbi.ac.ke/interop230/fhir/v4");
//        fhirConfig.updateReferral(serviceRequest);
        return SimpleObject.create("success", "true");
    }

    public static void postFhirResource(String fhirResource, String openHimUrl) throws Exception {
        HttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(openHimUrl);
        String token = "Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6IkU0MUU1QUM5RUIxNTlBMjc1NTY4NjM0MzIxMUJDQzAzMDMyMEUzMTZSUzI1NiIsIng1dCI6IjVCNWF5ZXNWbWlkVmFHTkRJUnZNQXdNZzR4WSIsInR5cCI6ImF0K2p3dCJ9.eyJpc3MiOiJodHRwczovL2RocGlkZW50aXR5c3RhZ2luZ2FwaS5oZWFsdGguZ28ua2UiLCJuYmYiOjE2OTM1NTk1MjIsImlhdCI6MTY5MzU1OTUyMiwiZXhwIjoxNjkzNjQ1OTIyLCJhdWQiOlsiREhQLkdhdGV3YXkiLCJESFAuUGFydG5lcnMiXSwic2NvcGUiOlsiREhQLkdhdGV3YXkiLCJESFAuUGFydG5lcnMiXSwiY2xpZW50X2lkIjoicGFydG5lci50ZXN0LmNsaWVudCIsImp0aSI6IjQxMDhBOEY2RkZDRTlFN0Q1M0ZGQkI0OUQzMDU1Q0VEIn0.T9go41MWh0cgoaX3YQDQqag9dUvbYEzYfaKvvs63QEMQ8iU72GtvaeoOhqS7Kzq-84ooaB73Lya4oM3Ua0kxk_jQ4HkMnG7o5NpYeMYXealoj2hTbCkgGta1XLVIter9Ozy7YAFMAaPPP_dBGb4kZQI9vWjSfyJh5ib_Hjq_J48OszhZOr3s9EMIXsTyL8SkzcjjY2rjJY05uaT0d3ev9RKOHC_Kc-dA-YkCE7i5c5TqBnvjU64iW3wcAWkM8LDvRDc9NZ7Pvw2mG2dTMf5vQ-uVNCswSolkoaPBJcGnTE0AHx9I1Ss1d2TekrIZcOI530yZOiGmj9UVgawTaY3edQ";

        StringEntity fhirResourceEntity = new StringEntity(fhirResource);
        httpPost.setEntity(fhirResourceEntity);
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setHeader("Authorization", token);
        System.out.println("TOKE ++++++++ " + token);

        HttpResponse response = httpClient.execute(httpPost);
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode >= 200 && statusCode < 300) {
            System.out.println("FHIR resource was successfully posted to the OpenHIM channel");
        } else {
            String responseBody = response.getEntity().toString();
            System.out.println("An error occurred while posting the FHIR resource to the OpenHIM channel. Status code: "
                    + statusCode + " Response body: " + responseBody);
        }
    }

    /**
     * Fetch client from CR
     */
    private JSONObject getCRPatient(String serverUrl) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        String stringResponse = "";
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
        }};

        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        URL url = new URL(serverUrl);

        HttpsURLConnection con = (HttpsURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        UpiUtilsDataExchange upiUtils = new UpiUtilsDataExchange();
        String authToken = upiUtils.getToken();

        System.out.println("TOKEN " + authToken);

        con.setRequestProperty("Authorization", "Bearer " + authToken);
        con.setRequestProperty("Accept", "application/json");
        con.setConnectTimeout(10000); // set timeout to 10 seconds

        con.setDoOutput(true);

        if (con.getResponseCode() == HttpsURLConnection.HTTP_OK) { //success
            BufferedReader in = null;
            in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            stringResponse = response.toString();

            try {
                JSONParser parser = new JSONParser();
                JSONObject responseObj = (JSONObject) parser.parse(stringResponse);
                JSONObject client = (JSONObject) responseObj.get("client");

                if (client != null) {
                    return client;
                }
            } catch (Exception var7) {
                var7.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Create client referral data from CR data
     */
    public void persistReferralData(JSONObject crClient, FhirConfig fhirConfig, org.hl7.fhir.r4.model.ServiceRequest fhirServiceRequest) {
        if(crClient == null){
            return;
        }
        ExpectedTransferInPatients expectedTransferInPatients = new ExpectedTransferInPatients();
        expectedTransferInPatients.setClientFirstName(String.valueOf(crClient.get("firstName")));
        expectedTransferInPatients.setClientMiddleName(String.valueOf(crClient.get("middleName")));
        expectedTransferInPatients.setClientLastName(String.valueOf(crClient.get("lastName")));
        expectedTransferInPatients.setNupiNumber(String.valueOf(crClient.get("clientNumber")));
        expectedTransferInPatients.setClientBirthDate(new Date());
        String gender = String.valueOf(crClient.get("gender"));
        if (gender.equalsIgnoreCase("male")) {
            gender = "M";
        } else if (gender.equalsIgnoreCase("female")) {
            gender = "F";
        }
        expectedTransferInPatients.setClientGender(gender);
        expectedTransferInPatients.setReferralStatus("ACTIVE");
        expectedTransferInPatients.setPatientSummary(fhirConfig.getFhirContext().newJsonParser().encodeResourceToString(fhirServiceRequest));
        expectedTransferInPatients.setServiceType("COMMUNITY");
        Context.getService(KenyaEMRILService.class).createPatient(expectedTransferInPatients);
        System.out.println("Successfully persisted in expected referrals model ==>");
    }

    public SimpleObject completeClientReferral(@RequestParam("patientId") Integer referral) throws Exception {
        KenyaEMRILService service = Context.getService(KenyaEMRILService.class);
        ExpectedTransferInPatients referred = service.getCommunityReferralsById(referral);
        Patient patient = registerReferredPatient(referred);
        if (patient != null) {
            referred.setReferralStatus("COMPLETED");
            referred.setPatient(patient);
            service.createPatient(referred);

            return SimpleObject.create("patientId", patient.getPatientId());
        }

        return SimpleObject.create("patientId", "");
    }

    public Location getDefaultLocation() {
        try {
            Context.addProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
            Context.addProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
            String GP_DEFAULT_LOCATION = "kenyaemr.defaultLocation";
            GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(GP_DEFAULT_LOCATION);
            return gp != null ? ((Location) gp.getValue()) : null;
        } finally {
            Context.removeProxyPrivilege(PrivilegeConstants.GET_LOCATIONS);
            Context.removeProxyPrivilege(PrivilegeConstants.GET_GLOBAL_PROPERTIES);
        }

    }

    /**
     * Create referred patient
     */
    private Patient registerReferredPatient(ExpectedTransferInPatients referredPatient) {
        List<Patient> results = Context.getPatientService().getPatients(referredPatient.getNupiNumber());
        //Assign  active referral_status attribute
        PersonAttribute referralStatusAttribute = new PersonAttribute();
        PersonAttributeType referralStatusAttributeType = Context.getPersonService().getPersonAttributeTypeByUuid("df7e9996-23b5-4f66-a799-97498d19850d");

        if (!results.isEmpty()) {
            if (referralStatusAttributeType != null) {
                referralStatusAttribute.setAttributeType(referralStatusAttributeType);
                referralStatusAttribute.setValue("Active");
                results.get(0).addAttribute(referralStatusAttribute);
            }
            return Context.getPatientService().savePatient(results.get(0));
        } else {
            Patient patient = new Patient();
            PersonName pn = new PersonName();
            pn.setGivenName(referredPatient.getClientFirstName());
            pn.setMiddleName(referredPatient.getClientMiddleName());
            pn.setFamilyName(referredPatient.getClientLastName());
            patient.addName(pn);
            patient.setBirthdate(new Date());
            patient.setGender(referredPatient.getClientGender());
            PatientIdentifierType nupiIdType = MetadataUtils.existing(PatientIdentifierType.class, NUPI);
            PatientIdentifier fetchedNupi = new PatientIdentifier(referredPatient.getNupiNumber(), nupiIdType, getDefaultLocation());
            patient.addIdentifier(fetchedNupi);

            PatientIdentifierType openMrsIdType = MetadataUtils.existing(PatientIdentifierType.class, OPENMRS_ID);
            String generated = Context.getService(IdentifierSourceService.class).generateIdentifier(openMrsIdType, "Registration");
            PatientIdentifier openMrsId = new PatientIdentifier(generated, openMrsIdType, getDefaultLocation());
            patient.addIdentifier(openMrsId);
            if (!patient.getPatientIdentifier().isPreferred()) {
                openMrsId.setPreferred(true);
            }

            if (referralStatusAttributeType != null) {
                referralStatusAttribute.setAttributeType(referralStatusAttributeType);
                referralStatusAttribute.setValue("Active");
                patient.addAttribute(referralStatusAttribute);
            }
            return Context.getPatientService().savePatient(patient);
        }
    }

    public SimpleObject addReferralCategoryAndReasons(@RequestParam("clientId") Integer clientId) throws Exception {

        //Update  referral category and reasons
        FhirConfig fhirConfig = Context.getRegisteredComponents(FhirConfig.class).get(0);

        ExpectedTransferInPatients patientReferral = Context.getService(KenyaEMRILService.class).getCommunityReferralsById(clientId);
        IParser parser = fhirConfig.getFhirContext().newJsonParser().setPrettyPrint(true);

        ServiceRequest serviceRequest;
        SimpleObject referralsDetailsObject = null;
        if (patientReferral != null) {

            serviceRequest = parser.parseResource(ServiceRequest.class, patientReferral.getPatientSummary());

            String category = "";
            if (!serviceRequest.getCategory().isEmpty()) {
                if (!serviceRequest.getCategory().get(0).getCoding().isEmpty()) {
                    category = serviceRequest.getCategory().get(0).getCoding().get(0).getDisplay();
                }
            }

        List<String> reasons = new ArrayList<>();

        for (CodeableConcept codeableConcept : serviceRequest.getReasonCode()) {
            if (!codeableConcept.getCoding().isEmpty()) {
                for (Coding code : codeableConcept.getCoding()) {
                    reasons.add(code.getDisplay());
                }
            }
        }

             referralsDetailsObject = SimpleObject.create("category", category,
                "reasonCode", String.join(", ", reasons)
        );
    }
        return referralsDetailsObject;
  }
}