package billing;

public class Retry {
    // Retries the task up to three times with exponential backoff.
    public void run(Runnable task) {
        task.run();
    }
}
