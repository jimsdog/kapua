package org.eclipse.kapua.service.device.managemet.job;

import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.job.engine.JobEngineFactory;
import org.eclipse.kapua.job.engine.JobEngineService;
import org.eclipse.kapua.job.engine.JobStartOptions;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.job.targets.JobTarget;
import org.eclipse.kapua.service.job.targets.JobTargetFactory;
import org.eclipse.kapua.service.job.targets.JobTargetListResult;
import org.eclipse.kapua.service.job.targets.JobTargetQuery;
import org.eclipse.kapua.service.job.targets.JobTargetService;
import org.eclipse.kapua.service.scheduler.trigger.Trigger;
import org.eclipse.kapua.service.scheduler.trigger.TriggerAttributes;
import org.eclipse.kapua.service.scheduler.trigger.TriggerFactory;
import org.eclipse.kapua.service.scheduler.trigger.TriggerListResult;
import org.eclipse.kapua.service.scheduler.trigger.TriggerQuery;
import org.eclipse.kapua.service.scheduler.trigger.TriggerService;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinition;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinitionAttributes;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinitionFactory;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinitionListResult;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinitionQuery;
import org.eclipse.kapua.service.scheduler.trigger.definition.TriggerDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessOnConnectProccessor {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessOnConnectProccessor.class);

    private static final KapuaLocator LOCATOR = KapuaLocator.getInstance();

    private static final JobEngineService JOB_ENGINE_SERVICE = LOCATOR.getService(JobEngineService.class);
    private static final JobEngineFactory JOB_ENGINE_FACTORY = LOCATOR.getFactory(JobEngineFactory.class);


    private static final JobTargetService JOB_TARGET_SERVICE = LOCATOR.getService(JobTargetService.class);
    private static final JobTargetFactory JOB_TARGET_FACTORY = LOCATOR.getFactory(JobTargetFactory.class);

    private static final TriggerDefinitionService TRIGGER_DEFINITION_SERVICE = LOCATOR.getService(TriggerDefinitionService.class);
    private static final TriggerDefinitionFactory TRIGGER_DEFINITION_FACTORY = LOCATOR.getFactory(TriggerDefinitionFactory.class);

    private static final TriggerService TRIGGER_SERVICE = LOCATOR.getService(TriggerService.class);
    private static final TriggerFactory TRIGGER_FACTORY = LOCATOR.getFactory(TriggerFactory.class);

    private static final TriggerDefinition DEVICE_CONNECT_TRIGGER;

    static {
        try {
            TriggerDefinitionQuery query = TRIGGER_DEFINITION_FACTORY.newQuery(null);

            query.setPredicate(query.attributePredicate(TriggerDefinitionAttributes.NAME, "Device Connect Trigger"));

            TriggerDefinitionListResult triggerDefinitions = TRIGGER_DEFINITION_SERVICE.query(query);

            if (triggerDefinitions.isEmpty()) {
                throw KapuaException.internalError("Error while searching 'Device Connect Trigger'");
            }

            DEVICE_CONNECT_TRIGGER = triggerDefinitions.getFirstItem();
        } catch (Exception e) {
            LOG.error("Error while initializing class", e);
        }
    }

    public static void processBirth(KapuaId scopeId, KapuaId deviceId) {

        try {
            JobTargetQuery jobTargetQuery = JOB_TARGET_FACTORY.newQuery(scopeId);

            jobTargetQuery.setPredicate(
                    jobTargetQuery.attributePredicate("jobTargetId", deviceId)
            );

            JobTargetListResult jobTargetListResult = JOB_TARGET_SERVICE.query(jobTargetQuery);

            for (JobTarget jt : jobTargetListResult.getItems()) {
                TriggerQuery triggerQuery = TRIGGER_FACTORY.newQuery(scopeId);

                triggerQuery.setPredicate(
                        triggerQuery.andPredicate(
                                triggerQuery.attributePredicate(TriggerAttributes.TRIGGER_DEFINITION_ID, DEVICE_CONNECT_TRIGGER.getId()),
                                triggerQuery.attributePredicate(TriggerAttributes.TRIGGER_PROPERTIES_TYPE, KapuaId.class.getName()),
                                triggerQuery.attributePredicate(TriggerAttributes.TRIGGER_PROPERTIES_VALUE, jt.getJobId())
                        )
                );

                TriggerListResult jobTriggers = TRIGGER_SERVICE.query(triggerQuery);

                for (Trigger t : jobTriggers.getItems()) {
                    JobStartOptions jobStartOptions = JOB_ENGINE_FACTORY.newJobStartOptions();

                    jobStartOptions.addTargetIdToSublist(jt.getId());
                    jobStartOptions.setFromStepIndex(jt.getStepIndex());
                    jobStartOptions.setEnqueue(true);

                    JOB_ENGINE_SERVICE.startJob(jt.getScopeId(), jt.getJobId(), jobStartOptions);
                }
            }

        } catch (Exception e) {
            LOG.error("Error while processing BIRTH message ");
        }
    }
}
