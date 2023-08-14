package org.openmrs.module.kenyaemrIL.api;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientProgram;
import org.openmrs.PersonName;
import org.openmrs.Program;
import org.openmrs.api.context.Context;
import org.openmrs.calculation.patient.PatientCalculationContext;
import org.openmrs.calculation.patient.PatientCalculationService;
import org.openmrs.calculation.result.CalculationResult;
import org.openmrs.module.kenyaemr.calculation.EmrCalculationUtils;
import org.openmrs.module.kenyaemr.calculation.library.hiv.LastWhoStageCalculation;
import org.openmrs.module.kenyaemr.calculation.library.hiv.art.LastCd4CountDateCalculation;
import org.openmrs.module.kenyaemr.metadata.IPTMetadata;
import org.openmrs.module.kenyaemr.metadata.TbMetadata;
import org.openmrs.module.kenyaemr.util.EmrUtils;
import org.openmrs.module.kenyaemr.util.EncounterBasedRegimenUtils;
import org.openmrs.module.kenyaemrIL.hivDicontinuation.Program_Discontinuation_Message;
import org.openmrs.module.kenyaemrIL.hivDicontinuation.artReferral.PATIENT_NCD;
import org.openmrs.module.kenyaemrIL.hivDicontinuation.artReferral.PATIENT_REFERRAL_INFORMATION;
import org.openmrs.module.kenyaemrIL.hivDicontinuation.artReferral.SERVICE_REQUEST_SUPPORTING_INFO;
import org.openmrs.module.kenyaemrIL.il.EXTERNAL_PATIENT_ID;
import org.openmrs.module.kenyaemrIL.il.ILMessage;
import org.openmrs.module.kenyaemrIL.il.INTERNAL_PATIENT_ID;
import org.openmrs.module.kenyaemrIL.il.MOTHER_NAME;
import org.openmrs.module.kenyaemrIL.il.PATIENT_ADDRESS;
import org.openmrs.module.kenyaemrIL.il.PATIENT_IDENTIFICATION;
import org.openmrs.module.kenyaemrIL.il.PATIENT_NAME;
import org.openmrs.module.kenyaemrIL.il.PHYSICAL_ADDRESS;
import org.openmrs.module.kenyaemrIL.il.utils.MessageHeaderSingleton;
import org.openmrs.module.kenyaemrIL.util.ILUtils;
import org.openmrs.module.metadatadeploy.MetadataUtils;
import org.openmrs.ui.framework.SimpleObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ILPatientDiscontinuation {
    static SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");

    public static ILMessage iLPatientWrapper(Patient patient, Encounter encounter) {
        ILMessage ilMessage = new ILMessage();
        PATIENT_IDENTIFICATION patientIdentification = new PATIENT_IDENTIFICATION();
        List<INTERNAL_PATIENT_ID> internalPatientIds = new ArrayList<INTERNAL_PATIENT_ID>();
        EXTERNAL_PATIENT_ID epd = new EXTERNAL_PATIENT_ID();
        INTERNAL_PATIENT_ID ipd;

//        Form the internal patient IDs
        for (PatientIdentifier patientIdentifier : patient.getIdentifiers()) {
            ipd = new INTERNAL_PATIENT_ID();
            if (patientIdentifier.getIdentifierType().getName().equalsIgnoreCase("Unique Patient Number")) {
                ipd.setAssigning_authority("CCC");
                ipd.setId(patientIdentifier.getIdentifier());
                ipd.setIdentifier_type("CCC_NUMBER");
                internalPatientIds.add(ipd);
//        Form the default external patient IDs
                epd.setAssigning_authority("MPI");
                epd.setIdentifier_type("GODS_NUMBER");
                patientIdentification.setExternal_patient_id(epd);
            }
        }
        //Set the patient name
        PATIENT_NAME patientname = new PATIENT_NAME();
        PersonName personName = patient.getPersonName();
        patientname.setFirst_name(personName.getGivenName() != null ? personName.getGivenName() : "");
        patientname.setMiddle_name(personName.getMiddleName() != null ? personName.getMiddleName() : "");
        patientname.setLast_name(personName.getFamilyName() != null ? personName.getFamilyName() : "");
        patientIdentification.setPatient_name(patientname);

        // Set to empty string unwanted patient details for viral load
        patientIdentification.setSex("");   //        Set the Gender, phone number and marital status
        patientIdentification.setPhone_number("");
        patientIdentification.setMarital_status("");
        patientIdentification.setDate_of_birth("");
        patientIdentification.setDate_of_birth_precision("");
        patientIdentification.setDeath_date("");
        patientIdentification.setDeath_indicator("");

        PATIENT_ADDRESS patientAddress = new PATIENT_ADDRESS();
        patientAddress.setPostal_address("");
        patientAddress.setPhysical_address(new PHYSICAL_ADDRESS());
        patientIdentification.setPatient_address(patientAddress);

        //Set mothers name
        patientIdentification.setMother_name(new MOTHER_NAME());

        patientIdentification.setPatient_name(patientname);
        patientIdentification.setInternal_patient_id(internalPatientIds);
        patientIdentification.setExternal_patient_id(epd);


        Program_Discontinuation_Message programDiscontinuationMessage = new Program_Discontinuation_Message();
        programDiscontinuationMessage.setTarget_program("HIV");
        for (Obs ob : encounter.getObs()) {
            if (ob.getConcept().getUuid().equals("161555AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                if (ob.getValueCoded().getUuid().equals("159492AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                    programDiscontinuationMessage.setDiscontinuation_reason("Transfer Out");
                    programDiscontinuationMessage.setService_request(referralInfo(encounter));
                }
                if (ob.getValueCoded().getUuid().equals("160034AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                    programDiscontinuationMessage.setDiscontinuation_reason("Death");
                }
                if (ob.getValueCoded().getUuid().equals("5240AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                    programDiscontinuationMessage.setDiscontinuation_reason("LTFU");
                }
                if (ob.getValueCoded().getUuid().equals("164349AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                    programDiscontinuationMessage.setDiscontinuation_reason("Stopped Treatment");
                }
            }
            if (ob.getConcept().getUuid().equals("164384AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                programDiscontinuationMessage.setEffective_discontinuation_date(formatter.format(ob.getValueDatetime()));
            }
            if (ob.getConcept().getUuid().equals("1543AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                patientIdentification.setDeath_date(formatter.format(ob.getValueDatetime()));
            }
            if (ob.getConcept().getUuid().equals("1599AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                patientIdentification.setDeath_indicator(ob.getValueCoded().getName().getName());
            }
        }
        ilMessage.setPatient_identification(patientIdentification);

        ilMessage.setDiscontinuation_message(programDiscontinuationMessage);
        return ilMessage;
    }

    public static PATIENT_REFERRAL_INFORMATION referralInfo(Encounter encounter) {
        //Service Request Message
        ServiceRequest referralRequest = new ServiceRequest();
        CodeableConcept codeableConcept = new CodeableConcept().addCoding(new Coding("https://hl7.org/fhir/r4/", "", ""));
        referralRequest.setId(encounter.getUuid());
        referralRequest.setCategory(Arrays.asList(codeableConcept));
        referralRequest.setCode(codeableConcept);
        String facilityMfl = MessageHeaderSingleton.getDefaultLocationMflCode(MessageHeaderSingleton.getDefaultLocation());

        PATIENT_REFERRAL_INFORMATION referralInformation = new PATIENT_REFERRAL_INFORMATION();
        SERVICE_REQUEST_SUPPORTING_INFO serviceRequestSupportingInfo = new SERVICE_REQUEST_SUPPORTING_INFO();
        referralInformation.setTransfer_status(ServiceRequest.ServiceRequestStatus.ACTIVE);
        referralInformation.setTransfer_intent(ServiceRequest.ServiceRequestIntent.ORDER);
        referralInformation.setTransfer_priority(ServiceRequest.ServiceRequestPriority.ASAP);
        referralInformation.setSending_facility_mflCode(facilityMfl);
        referralInformation.setReceiving_facility_mflCode("");
        for (Obs obs : encounter.getObs()) {
            if (obs.getConcept().getUuid().equals("159495AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                referralInformation.setReceiving_facility_mflCode(obs.getValueText().split("-")[0]);
            }
            if (obs.getConcept().getUuid().equals("160649AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                referralInformation.setTransfer_out_date(formatter.format(obs.getValueDatetime()));
            }
        }

        //Set patient's last vl and current regimen
        Encounter lastLabResultsEncounter = ILUtils.lastEncounter(encounter.getPatient(), Context.getEncounterService().getEncounterTypeByUuid("17a381d1-7e29-406a-b782-aa903b963c28"));
        List<Encounter> followUpEncounters = Context.getEncounterService().getEncounters(encounter.getPatient(), null, null, null, null, Arrays.asList(Context.getEncounterService().getEncounterTypeByUuid("a0034eee-1940-4e35-847f-97537a35d05e")), null, null, null, false);
        List<Encounter> enrolmentEncounters = Context.getEncounterService().getEncounters(encounter.getPatient(), null, null, null, null, Arrays.asList(Context.getEncounterService().getEncounterTypeByUuid("de78a6be-bfc5-4634-adc3-5f1a280455cc")), null, null, null, false);
        Encounter latestFollowUpEncounter = null;
        if (!followUpEncounters.isEmpty()) {
            latestFollowUpEncounter = followUpEncounters.get(followUpEncounters.size() - 1);
        }

        StringBuilder drugAllergies = new StringBuilder();
        StringBuilder otherAllergies = new StringBuilder();
        List<PATIENT_NCD> patientNcds = new ArrayList<>();
        if (latestFollowUpEncounter != null) {
            for (Obs obs : latestFollowUpEncounter.getObs()) {
                if (obs.getConcept().getConceptId() == 5096) {
                    serviceRequestSupportingInfo.setAppointment_date(formatter.format(obs.getValueDatetime()));
                    long difference_In_Time = obs.getValueDatetime().getTime() - latestFollowUpEncounter.getEncounterDatetime().getTime();
                    serviceRequestSupportingInfo.setDrug_days(String.valueOf(TimeUnit.MILLISECONDS.toDays(difference_In_Time) % 365));
                }
                if (obs.getConcept().getUuid().equals("5089AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                    serviceRequestSupportingInfo.setWeight(Double.toString(obs.getValueNumeric()));
                }
                if (obs.getConcept().getUuid().equals("5090AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                    serviceRequestSupportingInfo.setHeight(Double.toString(obs.getValueNumeric()));
                }
                if (obs.getConcept().getUuid().equals("1658AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                    serviceRequestSupportingInfo.setArv_adherence_outcome(obs.getValueCoded().getName().getName());
                }
                if (obs.getConcept().getUuid().equals("1193AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                    drugAllergies.append(" " + obs.getValueCoded().getName().getName());
                }
                if (obs.getConcept().getUuid().equals("160643AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                    otherAllergies.append(" " + obs.getValueCoded().getName().getName());
                }
                if (obs.getConcept().getUuid().equals("1284AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                    List<Obs> onsetDate = latestFollowUpEncounter.getObs()
                            .stream()
                            .filter(c -> c.getConcept().getUuid().equals("159948AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA") && c.getObsGroup().getUuid().equals(obs.getObsGroup().getUuid()))
                            .collect(Collectors.toList());
                    if (!onsetDate.isEmpty()) {
                        patientNcds.add(new PATIENT_NCD(obs.getValueCoded().getName().getName(), formatter.format(onsetDate.get(0).getValueDatetime()), ""));
                    }
                }
            }

        }
        serviceRequestSupportingInfo.setDrug_allergies(drugAllergies.toString());
        serviceRequestSupportingInfo.setOther_allergies(otherAllergies.toString());
        serviceRequestSupportingInfo.setPatient_ncds(patientNcds);


        PatientCalculationContext context = Context.getService(PatientCalculationService.class).createCalculationContext();
        context.setNow(new Date());

        // current who staging
        CalculationResult currentWhoStaging = EmrCalculationUtils.evaluateForPatient(LastWhoStageCalculation.class, null, encounter.getPatient());
        if (currentWhoStaging != null) {
            serviceRequestSupportingInfo.setWho_stage(((Obs) currentWhoStaging.getValue()).getValueCoded().getName().getName());
        } else {
            serviceRequestSupportingInfo.setWho_stage("");
        }

        // Current regimen
        Encounter lastDrugRegimenEditorEncounter = EncounterBasedRegimenUtils.getLastEncounterForCategory(encounter.getPatient(), "ARV");
        if (lastDrugRegimenEditorEncounter != null) {
            SimpleObject o = EncounterBasedRegimenUtils.buildRegimenChangeObject(lastDrugRegimenEditorEncounter.getAllObs(), lastDrugRegimenEditorEncounter);
            serviceRequestSupportingInfo.setCurrent_regimen(o.get("regimenShortDisplay").toString());
        }

        // current cd4
        CalculationResult cd4Results = EmrCalculationUtils.evaluateForPatient(LastCd4CountDateCalculation.class, null, encounter.getPatient());
        if (cd4Results != null && cd4Results.getValue() != null) {
            serviceRequestSupportingInfo.setCd4_value(((Obs) cd4Results.getValue()).getValueNumeric().toString());
            serviceRequestSupportingInfo.setCd4_date(formatter.format(((Obs) cd4Results.getValue()).getObsDatetime()));
        }

        // regimen change history
        List<SimpleObject> regimenChangeHistory = EncounterBasedRegimenUtils.getRegimenHistoryFromObservations(encounter.getPatient(), "ARV");
        serviceRequestSupportingInfo.setRegimen_change_history(regimenChangeHistory);
        
        Integer latestVLConcept = 856;
        Integer LDLQuestionConcept = 1305;
        if (lastLabResultsEncounter != null) {
            for (Obs obs : lastLabResultsEncounter.getObs()) {
                //set vl sample collection date
                if (obs.getConcept().getConceptId().equals(latestVLConcept)) {
                    serviceRequestSupportingInfo.setLast_vl_date(String.valueOf(obs.getObsDatetime()));
                } else if (obs.getConcept().getConceptId().equals(LDLQuestionConcept)) {
                    serviceRequestSupportingInfo.setLast_vl_date(String.valueOf(obs.getObsDatetime()));
                }

                //set vl result
                if (obs.getConcept().getConceptId().equals(latestVLConcept)) {
                    serviceRequestSupportingInfo.setViral_load(String.valueOf(obs.getValueNumeric()));
                } else if (obs.getConcept().getConceptId().equals(LDLQuestionConcept)) {
                    serviceRequestSupportingInfo.setViral_load("LDL");
                }
            }
        }

        if (!enrolmentEncounters.isEmpty()) {
            for (Encounter enrolment : enrolmentEncounters) {
                //Filter patient type obs record
                List<Obs> patientType = enrolment.getObs()
                        .stream()
                        .filter(c -> c.getConcept().getConceptId() == 164932)
                        .collect(Collectors.toList());
                if (!patientType.isEmpty()) {
                    if (!Arrays.asList(new Integer[]{164931, 159833}).contains(patientType.get(0).getValueCoded().getConceptId())) {
                        for (Obs obs : enrolment.getObs()) {
                            if (obs.getConcept().getUuid().equals("160554AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                                serviceRequestSupportingInfo.setDate_confirmed_positive(formatter.format(obs.getValueDatetime()));
                            }
                            if (obs.getConcept().getUuid().equals("160555AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                                serviceRequestSupportingInfo.setDate_first_enrolled(formatter.format(obs.getValueDatetime()));
                            }
                            if (obs.getConcept().getUuid().equals("159599AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                                serviceRequestSupportingInfo.setDate_started_art_at_transferring_facility(formatter.format(enrolment.getEncounterDatetime()));
                            }
                            if (obs.getConcept().getUuid().equals("160540AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")) {
                                serviceRequestSupportingInfo.setEntry_point(obs.getValueCoded().getName().getName());
                            }
                        }
                    }
                }
            }
        }
        //IPT Data
        Program iptProgram = MetadataUtils.existing(Program.class, IPTMetadata._Program.IPT);
        Encounter lastIptOutcomeEncounter = EmrUtils.lastEncounter(encounter.getPatient(), Context.getEncounterService().getEncounterTypeByUuid(IPTMetadata._EncounterType.IPT_OUTCOME));
        List<PatientProgram> patientPrograms = Context.getProgramWorkflowService().getPatientPrograms(encounter.getPatient(), iptProgram, null, null, null, null, false);
        List<PatientProgram> patientIptProgram = patientPrograms.stream()
                .filter(pp -> pp.getProgram().getUuid().equals(IPTMetadata._Program.IPT))
                .collect(Collectors.toList());
        if (!patientIptProgram.isEmpty()) {
            serviceRequestSupportingInfo.setTpt_start_date(formatter.format(patientIptProgram.get(0).getDateEnrolled()));
            if (lastIptOutcomeEncounter != null) {
                serviceRequestSupportingInfo.setTpt_end_date(formatter.format(patientIptProgram.get(0).getDateCompleted()));
                if (lastIptOutcomeEncounter != null) {
                    List<Obs> stopReasonList = lastIptOutcomeEncounter.getObs().stream().filter(ob -> ob.getConcept().getUuid().equals("161555AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).collect(Collectors.toList());
                    serviceRequestSupportingInfo.setTpt_end_reason(stopReasonList.get(0).getValueCoded().getName().getName());
                }
            }
        }

        //TB Data
        Program tbProgram = MetadataUtils.existing(Program.class, TbMetadata._Program.TB);
        Encounter lastTbOutcomeEncounter = EmrUtils.lastEncounter(encounter.getPatient(), Context.getEncounterService().getEncounterTypeByUuid(TbMetadata._EncounterType.TB_DISCONTINUATION));
        List<PatientProgram> patientTbPrograms = Context.getProgramWorkflowService().getPatientPrograms(encounter.getPatient(), tbProgram, null, null, null, null, false);
        List<PatientProgram> patientTbProgram = patientTbPrograms.stream()
                .filter(pp -> pp.getProgram().getUuid().equals(TbMetadata._Program.TB))
                .collect(Collectors.toList());
        if (!patientTbProgram.isEmpty()) {
            serviceRequestSupportingInfo.setTb_start_date(formatter.format(patientTbProgram.get(0).getDateEnrolled()));
            if (lastTbOutcomeEncounter != null) {
                serviceRequestSupportingInfo.setTb_end_date(formatter.format(patientTbProgram.get(0).getDateCompleted()));
                List<Obs> stopReasonList = lastTbOutcomeEncounter.getObs().stream().filter(ob -> ob.getConcept().getUuid().equals("159786AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).collect(Collectors.toList());
                serviceRequestSupportingInfo.setTb_end_reason(stopReasonList.get(0).getValueCoded().getName().getName());
            }
        }


        referralInformation.setSupporting_info(serviceRequestSupportingInfo);

        return referralInformation;
    }
}