package mage.remote;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class SessionImplTest {

    private static final long WAIT_SECONDS = 5;

    @Test
    @DisplayName("execute remote work with the supplied executor")
    void suppliedExecutor() throws Throwable {
        AtomicInteger executionCount = new AtomicInteger();
        Executor directExecutor = command -> {
            executionCount.incrementAndGet();
            command.run();
        };
        SessionImpl session = new SessionImpl(null, directExecutor);
        SessionImpl.RemotingTask task = session.new RemotingTask() {
            @Override
            public boolean work() {
                return true;
            }
        };

        assertThat(task.doWork()).isTrue();
        assertThat(executionCount).hasValue(1);
    }

    @Test
    @DisplayName("propagate the original remote work error")
    void originalError() {
        AssertionError expected = new AssertionError("remote failure");
        SessionImpl session = new SessionImpl(null, Runnable::run);
        SessionImpl.RemotingTask task = session.new RemotingTask() {
            @Override
            public boolean work() throws Throwable {
                throw expected;
            }
        };

        Throwable actual = catchThrowable(task::doWork);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    @DisplayName("interrupt running remote work on cancellation")
    void cancellationInterruptsWork() throws Exception {
        CountDownLatch workStarted = new CountDownLatch(1);
        CountDownLatch workInterrupted = new CountDownLatch(1);
        AtomicReference<Throwable> completion = new AtomicReference<>();
        Executor asynchronousExecutor = command -> {
            Thread workerThread = new Thread(command, "SessionImplTest-worker");
            workerThread.setDaemon(true);
            workerThread.start();
        };
        SessionImpl session = new SessionImpl(null, asynchronousExecutor);
        SessionImpl.RemotingTask task = session.new RemotingTask() {
            @Override
            public boolean work() throws Throwable {
                workStarted.countDown();
                try {
                    new CountDownLatch(1).await();
                    return true;
                } catch (InterruptedException e) {
                    workInterrupted.countDown();
                    throw e;
                }
            }
        };
        Thread callerThread = new Thread(
                () -> completion.set(catchThrowable(task::doWork)),
                "SessionImplTest-caller"
        );
        callerThread.setDaemon(true);
        callerThread.start();

        assertThat(workStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        task.cancel();

        assertThat(workInterrupted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        callerThread.join(TimeUnit.SECONDS.toMillis(WAIT_SECONDS));
        assertThat(callerThread.isAlive()).isFalse();
        assertThat(completion.get()).isInstanceOf(CancellationException.class);
    }
}
