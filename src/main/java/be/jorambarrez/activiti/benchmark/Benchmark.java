package be.jorambarrez.activiti.benchmark;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.interceptor.CommandInterceptor;
import org.activiti.engine.impl.util.LogUtil;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import be.jorambarrez.activiti.benchmark.execution.BasicBenchmarkExecution;
import be.jorambarrez.activiti.benchmark.execution.BenchmarkExecution;
import be.jorambarrez.activiti.benchmark.execution.FixedThreadPoolBenchmarkExecution;
import be.jorambarrez.activiti.benchmark.output.BenchmarkOuput;
import be.jorambarrez.activiti.benchmark.output.BenchmarkResult;
import be.jorambarrez.activiti.benchmark.profiling.ProfilingInterceptor;
import be.jorambarrez.activiti.benchmark.profiling.ProfilingLogParser;

/**
 * Main class that contains the logic to execute the benchmark.
 * 
 * @author jbarrez
 */
public class Benchmark {

	static {
		LogUtil.readJavaUtilLoggingConfigFromClasspath();
	}

	private static String[] PROCESSES = { 
		"process01", 
		"process02",
		"process03",
		"process04",
		"process05",
		"process-usertask-01",
		"process-usertask-02",
		"process-usertask-03" 
	};

	private static int maxNrOfThreadsInThreadPool;

	private static String historyValue;
	private static String configurationValue;
	private static boolean profiling;

	private static List<BenchmarkResult> fixedPoolSequentialResults = new ArrayList<BenchmarkResult>();
	private static List<BenchmarkResult> fixedPoolRandomResults = new ArrayList<BenchmarkResult>();

	public static void main(String[] args) throws Exception {

		long start = System.currentTimeMillis();

		if (!readAndValidateParams(args)) {
			System.exit(1);
		}

		boolean historyEnabled = !historyValue.equals("none");
		System.out.println("History enabled : " + historyEnabled);

		int nrOfExecutions = Integer.valueOf(args[0]);
		maxNrOfThreadsInThreadPool = Integer.valueOf(args[1]);

		ProcessEngine processEngine = getProcessEngine(configurationValue, historyEnabled);
		executeBenchmarks(processEngine, historyEnabled, nrOfExecutions, maxNrOfThreadsInThreadPool);
		writeHtmlReport();

		if (profiling) {
			System.out.println();
			System.out.println("Generating profile report");
			System.out.println();
			
			// flushing writer and sleeping a bit, just to be sure
			ProfilingInterceptor.fileWriter.flush();
			Thread.sleep(5000L);
			
			ProfilingLogParser profilingLogParser = new ProfilingLogParser();
			profilingLogParser.execute();
		}

		System.out.println("Benchmark completed. Ran for "
				+ ((System.currentTimeMillis() - start) / 1000L) + " seconds");
	}

	private static ProcessEngine getProcessEngine(String configuration, boolean historyEnabled) {
		if (configuration.equals("default")) {
			
			System.out.println("Using DEFAULT config");
			// Not doing the 'official way' here, but it is needed if we want
			// the property resolving to work

			ClassPathXmlApplicationContext appCtx = new ClassPathXmlApplicationContext("activiti.cfg.xml");
			ProcessEngineConfiguration processEngineConfiguration = appCtx.getBean(ProcessEngineConfiguration.class);

			System.out.println("[Process engine configuration info]:");
			System.out.println("url : " + processEngineConfiguration.getJdbcUrl());
			System.out.println("driver : " + processEngineConfiguration.getJdbcDriver());

			if (profiling) {
				List<CommandInterceptor> interceptors = new ArrayList<CommandInterceptor>();
				interceptors.add(new ProfilingInterceptor());
				((ProcessEngineConfigurationImpl) processEngineConfiguration)
						.setCustomPreCommandInterceptorsTxRequired(interceptors);
			}

			return processEngineConfiguration.buildProcessEngine();
		} else if (configuration.equals("spring")) {
			System.out.println("Using SPRING config");
			ClassPathXmlApplicationContext appCtx = new ClassPathXmlApplicationContext("spring-context.xml");

			if (profiling) {
				throw new RuntimeException("Profiling is currently only possible in default configuration");
			}

			return appCtx.getBean(ProcessEngine.class);
		}
		throw new RuntimeException("Invalid config: only 'default' and 'spring' are supported");
	}

	private static void executeBenchmarks(ProcessEngine processEngine,
			boolean historyEnabled, int nrOfProcessExecutions,
			int maxNrOfThreadsInThreadPool) {

		// Deploy test processes
		System.out.println("Deploying test processes");
		for (String process : PROCESSES) {
			processEngine.getRepositoryService().createDeployment()
					.addClasspathResource(process + ".bpmn20.xml").deploy();
		}
		System.out.println("Finished deploying test processes");

		// Single thread benchmark
		System.out.println(new Date() + " - benchmarking with one thread.");
		BenchmarkExecution singleThreadBenchmark = new BasicBenchmarkExecution(processEngine, PROCESSES);
		fixedPoolSequentialResults.add(singleThreadBenchmark.sequentialExecution(PROCESSES, nrOfProcessExecutions, historyEnabled));
		fixedPoolRandomResults.add(singleThreadBenchmark.randomExecution(PROCESSES, nrOfProcessExecutions, historyEnabled));

		// Multiple threads - fixed pool benchmark
		for (int nrOfWorkerThreads = 2; nrOfWorkerThreads <= maxNrOfThreadsInThreadPool; nrOfWorkerThreads++) {

			System.out.println(new Date() + " - benchmarking with fixed threadpool of " + nrOfWorkerThreads + " threads.");
			BenchmarkExecution fixedPoolBenchMark = new FixedThreadPoolBenchmarkExecution(processEngine, nrOfWorkerThreads, PROCESSES);
			fixedPoolSequentialResults.add(fixedPoolBenchMark.sequentialExecution(PROCESSES, nrOfProcessExecutions, historyEnabled));
			fixedPoolRandomResults.add(fixedPoolBenchMark.randomExecution(PROCESSES, nrOfProcessExecutions, historyEnabled));
		}
	}

	private static void writeHtmlReport() {
		BenchmarkOuput output = new BenchmarkOuput();
		output.start("Activiti 5.10-SNAPSHOT basic benchmark results");

		for (int i = 1; i <= maxNrOfThreadsInThreadPool; i++) {
			output.addBenchmarkResult("Fixed thread pool (" + i + "threads), sequential", fixedPoolSequentialResults.get(i - 1));
		}
		output.generateChartOfPreviousAddedBenchmarkResults(false);

		for (int i = 1; i <= maxNrOfThreadsInThreadPool; i++) {
			output.addBenchmarkResult("Fixed thread pool (" + i + "threads), randomized", fixedPoolRandomResults.get(i - 1));
		}
		output.generateChartOfPreviousAddedBenchmarkResults(true);

		output.writeOut();
	}

	/**
	 * Validates the commandline arguments.
	 * 
	 * @return True if they are OK
	 */
	private static boolean readAndValidateParams(String[] args) {
		// length check
		if (args.length != 2) {
			System.err.println();
			System.err.println("Wrong number of arguments");
			System.err.println();
			System.err.println("Usage: java -Xms512M -Xmx1024M -Dhistory={none|activity|audit|full} -Dconfig={default|spring} -Dprofiling -D{jdbcOption}={jdbcValue} -jar activiti-basic-benchmark.jar "
							+ "<nr_of_executions> <max_nr_of_threads_in_threadpool> ");
			System.err.println();
			System.err.println("Options:");
			System.err.println("-DjdbcUrl={value}");
			System.err.println("-DjdbcDriver={value}");
			System.err.println("-DjdbcUsername={value}");
			System.err.println("-DjdbcPassword={value}");
			System.err.println("-DjdbcMaxActiveConnections={value}");
			System.err.println("-DjdbcMaxIdleConnections={value}");
			System.err.println();
			System.err.println();
			return false;
		}

		if (!System.getProperties().containsKey("history")) {
			System.out
					.println("No history config specified, using default value 'audit");
			System.getProperties().put("history", "audit");
		}
		historyValue = (String) System.getProperties().get("history");

		if (!System.getProperties().containsKey("config")) {
			System.out.println("No config specified, using default");
			System.getProperties().put("config", "default");
		}
		configurationValue = (String) System.getProperties().get("config");

		if (configurationValue != null && !configurationValue.equals("default")
				&& !configurationValue.equals("spring")) {
			System.err.println("Invalid configuration option: only default|spring are currently supported");
			return false;
		}

		if (historyValue != null && !historyValue.equals("none")
				&& !historyValue.equals("activity")
				&& !historyValue.equals("audit")
				&& !historyValue.equals("full")) {
			System.err.println("Invalid history option: only none|activity|audit|full are currently supported");
			return false;
		}

		if (System.getProperties().containsKey("profiling")) {
			profiling = true;
		}

		try {
			Integer.parseInt(args[0]);
		} catch (NumberFormatException e) {
			System.err.println("Wrong argument type for nr_of_executions: use an integer");
			return false;
		}

		try {
			Integer.parseInt(args[1]);
		} catch (NumberFormatException e) {
			System.err.println("Wrong argument type for max_nr_of_threads_in_threadpool: use an integer");
			return false;
		}

		return true;
	}

}
