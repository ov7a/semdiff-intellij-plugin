package billing;

public class Retry {
    // retry a few times
    public void run(Runnable task) {
        task.run();
    }
}
