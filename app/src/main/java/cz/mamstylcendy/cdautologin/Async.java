package cz.mamstylcendy.cdautologin;

import android.content.Context;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Async {

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static void run(Runnable task) {
        executor.execute(task);
    }

    public static <R> void run(Context context, Callable<R> task, Consumer<R> callback, Consumer<Throwable> onError) {
        Executor mainExecutor = ContextCompat.getMainExecutor(context);
        executor.execute(() -> {
            try {
                R result = task.call();
                mainExecutor.execute(() -> callback.accept(result));
            } catch (Throwable th) {
                mainExecutor.execute(() -> onError.accept(th));
            }
        });
    }

    public static <R> void runWithinLifecycle(Context context, LifecycleOwner lifecycleOwner, Callable<R> task, Consumer<R> callback, Consumer<Throwable> onError) {
        run(context, task, withinLifecycle(lifecycleOwner, callback), withinLifecycle(lifecycleOwner, onError));
    }

    public static <R> void runWithinLifecycle(AppCompatActivity activity, Callable<R> task, Consumer<R> callback, Consumer<Throwable> onError) {
        runWithinLifecycle(activity, activity, task, callback, onError);
    }

    public static <T> Consumer<T> withinLifecycle(LifecycleOwner lifecycleOwner, Consumer<T> callback) {
        return (T value) -> {
            if (lifecycleOwner.getLifecycle().getCurrentState() != Lifecycle.State.DESTROYED) {
                callback.accept(value);
            }
        };
    }
}
