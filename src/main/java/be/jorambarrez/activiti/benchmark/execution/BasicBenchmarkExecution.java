package be.jorambarrez.activiti.benchmark.execution;

import java.util.Date;
import java.util.List;

import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.repository.Deployment;

import be.jorambarrez.activiti.benchmark.output.BenchmarkResult;
import be.jorambarrez.activiti.benchmark.util.Utils;

/**
 * Basic way of running the benchmark: 1 thread, sequentially executing the
 * processes on by one.
 *
 * @author jbarrez
 */
public class BasicBenchmarkExecution implements BenchmarkExecution {

    protected ProcessEngine processEngine;

    protected String[] processes;

    protected RuntimeService runtimeService;

    protected long countBefore;

    protected long countAfter;

    public BasicBenchmarkExecution(ProcessEngine processEngine, String[] processes) {
        this.processEngine = processEngine;
        this.processes = processes;
        this.runtimeService = processEngine.getRuntimeService();
    }

    public BenchmarkResult sequentialExecution(String[] processes, int nrOfProcessExecutions, boolean history) {

        countProcessesBeforeBenchmark();
        BenchmarkResult result = new BenchmarkResult(1);

        for (String process : processes) {
            
        	System.out.println(new Date() + " : [SEQ]Starting " + nrOfProcessExecutions + " of process " + process);
            long allProcessesStart = System.currentTimeMillis();
                       
            for (int i = 0; i < nrOfProcessExecutions; i++) {
                ExecuteProcessRunnable executeProcessRunnable = new ExecuteProcessRunnable(process, processEngine);
                executeProcessRunnable.run();
                result.addProcessInstanceMeasurement(process, executeProcessRunnable.getDuration());
            }
            
            long allProcessesEnd = System.currentTimeMillis();
            result.addTotalTimeMeasurementForProcess(process, nrOfProcessExecutions, allProcessesEnd - allProcessesStart);
        }

        if (history) {
            countProcessesAfterBenchmark();
            verifyCounts(nrOfProcessExecutions * processes.length);
        }

        cleanAndDeploy();
        return result;
    }

    public BenchmarkResult randomExecution(String[] processes, int totalNrOfExecutions, boolean history) {

        countProcessesBeforeBenchmark();
        BenchmarkResult result = new BenchmarkResult(1);

        String[] randomizedProcesses = Utils.randomArray(processes, totalNrOfExecutions);
        System.out.println(new Date() + " : [RND]Starting " + totalNrOfExecutions + " random processes ");

        long allProcessesStart = System.currentTimeMillis();

        for (String randomProcess : randomizedProcesses) {
        	 new ExecuteProcessRunnable(randomProcess, processEngine).run();
        }

        long allProcessesEnd = System.currentTimeMillis();
        result.addTotalTimeMeasurementForProcess(Utils.toString(processes), totalNrOfExecutions, allProcessesEnd - allProcessesStart);

        if (history) {
            countProcessesAfterBenchmark();
            verifyCounts(totalNrOfExecutions);
        }

        cleanAndDeploy();
        return result;
    }


 /* -----------------------------------
  * HELPER METHODS FOR PROCESS COUNTING
  * ----------------------------------- */


    protected void cleanAndDeploy() {
        System.out.println(new Date() + " : Removing deployments");

        RepositoryService repositoryService = processEngine.getRepositoryService();
        List<Deployment> deployments = repositoryService.createDeploymentQuery().list();
        for (Deployment deployment : deployments) {
            repositoryService.deleteDeployment(deployment.getId(), true);
        }

        HistoryService historyService = processEngine.getHistoryService();
        System.out.println(new Date() + " : Removing " + historyService.createHistoricProcessInstanceQuery().count() + " historic process instances");
        List<HistoricProcessInstance> processInstances = historyService.createHistoricProcessInstanceQuery().listPage(0, 100);
        int processesDeleted = 0;
        while (processInstances.size() > 0) {
            for (HistoricProcessInstance historicProcessInstance : processInstances) {
                historyService.deleteHistoricProcessInstance(historicProcessInstance.getId());
                processesDeleted++;
            }

            if (processesDeleted % 500 == 0) {
                System.out.println("Deleted " + processesDeleted + " processes");
            }

            processInstances = historyService.createHistoricProcessInstanceQuery().listPage(0, 100);
        }

        long count = countNrOfEndedProcesses();
        if (count != 0) {
            throw new RuntimeException("Recreating DB schema failed: found " + count + " historic process instances");
        }

        System.out.println(new Date() + " : Deploying test processes");
        for (String process : processes) {
            processEngine.getRepositoryService().createDeployment()
                    .addClasspathResource(process + ".bpmn20.xml").deploy();
        }
    }

    protected void countProcessesBeforeBenchmark() {
        this.countBefore = countNrOfEndedProcesses();
    }

    protected void countProcessesAfterBenchmark() {
        this.countAfter = countNrOfEndedProcesses();
    }

    protected void verifyCounts(int expected) {
        System.out.print(new Date() + " : Counting finished processes [expected:" + expected +"] ... ");
        long finishedProcesses = countAfter - countBefore;
        if (finishedProcesses != expected) {
            throw new RuntimeException("Error: expected to have " + expected +
                    " processes executions, but got " + finishedProcesses + " instead.");
        }
        System.out.println("OK");
    }

    /**
     * Counts the nr of ended processes.
     */
    private long countNrOfEndedProcesses() {
        return processEngine.getHistoryService().createHistoricProcessInstanceQuery().finished().count();
    }

}
