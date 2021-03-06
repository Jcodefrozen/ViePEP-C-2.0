package at.ac.tuwien.infosys.viepepc.scheduler.frincu.container;

import at.ac.tuwien.infosys.viepepc.library.entities.container.Container;
import at.ac.tuwien.infosys.viepepc.library.entities.virtualmachine.VirtualMachine;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.ProcessStep;
import at.ac.tuwien.infosys.viepepc.library.entities.workflow.WorkflowElement;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerConfigurationNotFoundException;
import at.ac.tuwien.infosys.viepepc.library.registry.impl.container.ContainerImageNotFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.frincu.AbstractContainerProvisioningImpl;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.OptimizationResult;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.SchedulerAlgorithm;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.exceptions.NoVmFoundException;
import at.ac.tuwien.infosys.viepepc.scheduler.main.impl.exceptions.ProblemNotSolvedException;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Created by philippwaibel on 30/09/2016.
 */
@Slf4j
@Component
@Profile("StartParNotExceedContainer")
public class StartParNotExceedContainerImpl extends AbstractContainerProvisioningImpl implements SchedulerAlgorithm {

    private Map<WorkflowElement, VirtualMachine> vmStartedBecauseOfWorkflow = new HashMap<>();

    @Override
    public void initializeParameters() {

    }

    @Override
    public OptimizationResult optimize(DateTime tau_t) throws ProblemNotSolvedException {

        OptimizationResult optimizationResult = new OptimizationResult();

        try {
            workflowUtilities.setFinishedWorkflows();

            List<WorkflowElement> runningWorkflowInstances = getRunningWorkflowInstancesSorted();
            List<VirtualMachine> availableVms = getRunningVms();
            List<ProcessStep> nextProcessSteps = getNextProcessStepsSorted(runningWorkflowInstances);

            if (nextProcessSteps == null || nextProcessSteps.size() == 0) {
                return optimizationResult;
            }


//            if(availableVms.size() < runningWorkflowInstances.size()) {
//                int newVMs = runningWorkflowInstances.size() - availableVms.size();
//                for(int i = 0; i < newVMs; i++) {
//                    VirtualMachineInstance vm = startNewDefaultVm(optimizationResult);
//                    availableVms.add(vm);
//                    optimizationResult.addVirtualMachine(vm);
//                }
//            }

            if (availableVms.size() == 0) {
                availableVms.add(startNewDefaultVm(optimizationResult));
            }


            availableVms.sort(Comparator.comparing(VirtualMachine::getStartupTime));

            for (ProcessStep processStep : nextProcessSteps) {

                boolean deployed = false;
                Container container = getContainer(processStep);
                for (VirtualMachine vm : availableVms) {
                    long remainingBTU = getRemainingLeasingDuration(new DateTime(), vm);
                    if (remainingBTU > (processStep.getExecutionTime() + container.getContainerImage().getDeployTime())) {
                        if (checkIfEnoughResourcesLeftOnVM(vm, container, optimizationResult)) {
                            deployed = true;
                            deployContainerAssignProcessStep(processStep, container, vm, optimizationResult);
                            break;
                        }
                    }
                }

                if (!deployed && availableVms.size() < runningWorkflowInstances.size()) {
                    try {
                        VirtualMachine vm = startNewVMDeployContainerAssignProcessStep(processStep, optimizationResult);
                        availableVms.add(vm);
                    } catch (NoVmFoundException e) {
                        log.error("Could not find a VM. Postpone execution.");
                    }

                }
            }
        } catch (ContainerImageNotFoundException | ContainerConfigurationNotFoundException ex) {
            log.error("Container image or configuration not found", ex);
            throw new ProblemNotSolvedException();
        } catch (NoVmFoundException | Exception ex) {
            log.error("EXCEPTION", ex);
            throw new ProblemNotSolvedException();
        }

        return optimizationResult;
    }

    @Override
    public Future<OptimizationResult> asyncOptimize(DateTime tau_t) throws ProblemNotSolvedException {
        return new AsyncResult<>(optimize(tau_t));
    }

}
