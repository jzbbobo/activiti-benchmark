package be.jorambarrez.activiti.benchmark.execution;

import org.activiti.engine.ProcessEngine;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FixedThreadPoolBenchmarkExecution extends ThreadPooledBenchmarkExecution {
	
	public FixedThreadPoolBenchmarkExecution(ProcessEngine processEngine, int nrOfWorkerThreads, String[] processes) {
		super(processEngine, processes);
		this.nrOfWorkerThreads = nrOfWorkerThreads;		
	}
	
	protected ExecutorService getExecutorService() {
		return Executors.newFixedThreadPool(nrOfWorkerThreads);
	}

}
