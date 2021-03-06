package ru.stas.cadence.samples.LocalActivityWorkflow;

import com.uber.cadence.DomainAlreadyExistsError;
import com.uber.cadence.RegisterDomainRequest;
import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Hello {
  /**
   * Hello World Cadence workflow that executes a single activity. Requires a local instance the
   * Cadence service to be running.
   */

  static final String TASK_LIST = "HelloActivity";
  private static final String DOMAIN = "sample";

  /**
   * Workflow interface has to have at least one method annotated with @WorkflowMethod.
   */
  public interface GreetingWorkflow {
    /**
     * @return greeting string
     */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 240000, taskList = TASK_LIST)
    String getGreeting(String name);
  }

  /**
   * Activity interface is just a POJO.
   */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 8)
    String composeGreeting(String greeting);
  }

  /**
   * GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting.
   */
  public static class GreetingWorkflowImpl implements Hello.GreetingWorkflow {

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private Hello.GreetingActivities activities;

    @Override
    public String getGreeting(String name) {
   /* RetryOptions ro = new RetryOptions.Builder()
        .setInitialInterval(Duration.ofSeconds(1))
        .setMaximumAttempts(100000)
          .setDoNotRetry(DoNotRetryOnTimeoutException.class)
          .build();
      LocalActivityOptions lao = new LocalActivityOptions.Builder()
          .setRetryOptions(ro)
          .build();*/
      activities =
          Workflow.newLocalActivityStub(Hello.GreetingActivities.class);
 /*     CompletablePromise<String> result = Workflow.newPromise();
      CancellationScope scope =
          Workflow.newCancellationScope(
              () -> {
                result.completeFrom(Async.function(activities::composeGreeting,"composeGreeting"));
              });
      scope.run();*/
      try {
        return activities.composeGreeting("1");
      } catch (RuntimeException e) {
        System.out.println("WE GOT RuntimeException");
        return "FUCK";
      }
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public String composeGreeting(String greeting) {
      /*  System.out.println("Activity composeGreeting. Started");
        CloseableHttpClient httpclient = HttpClients.createDefault();

        HttpGet get = new HttpGet("http://127.0.0.1:8099/longoperation");
        try {
          System.out.println("Activity composeGreeting. Send long request");*/
      try {
        Thread.sleep(9000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      throw new DoNotRetryOnTimeoutException(new RuntimeException("BOOM2"));
          //CloseableHttpResponse response = httpclient.execute(get);
         // System.out.println("Activity composeGreeting. Status " + response.getStatusLine());
          //System.out.println("Activity composeGreeting. Get response");
    /*    } catch (IOException e) {
          System.out.println("Activity composeGreeting. Error");
          throw new RuntimeException(e);
        }
      System.out.println("Activity composeGreeting. Completed");Completed
        return greeting + "!";*/
    }
  }

  private static void registerDomain() {
    IWorkflowService cadenceService = new WorkflowServiceTChannel();
    RegisterDomainRequest request = new RegisterDomainRequest();
    request.setDescription("Java Samples");
    request.setEmitMetric(false);
    request.setName(DOMAIN);
    int retentionPeriodInDays = 1;
    request.setWorkflowExecutionRetentionPeriodInDays(retentionPeriodInDays);
    try {
      cadenceService.RegisterDomain(request);
      System.out.println(
          "Successfully registered domain \""
              + DOMAIN
              + "\" with retentionDays="
              + retentionPeriodInDays);

    } catch (DomainAlreadyExistsError e) {
      log.error("Domain \"" + DOMAIN + "\" is already registered");

    } catch (TException e) {
      log.error("Error occurred", e);
    }
  }

  public static void main(String[] args) {
    registerDomain();
    // Start a worker that hosts both workflow and activity implementations.
   // Worker.FactoryOptions fo = new Worker.FactoryOptions.Builder().setMaxWorkflowThreadCount(10).setDisableStickyExecution(true).build();

   // Worker.Factory factory = new Worker.Factory(DOMAIN, fo);
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);

    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(Hello.GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new Hello.GreetingActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    Hello.GreetingWorkflow workflow = workflowClient.newWorkflowStub(Hello.GreetingWorkflow.class);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    System.out.println(greeting);
    System.exit(0);
  }

}
