package com.example.seqrpay;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// Simple utility class for handling background and main thread execution
public class AppExecutors {

    private static final Object LOCK = new Object();
    private static AppExecutors sInstance;
    private final Executor diskIO; // For database operations
    private final Executor mainThread;

    private AppExecutors(Executor diskIO, Executor mainThread) {
        this.diskIO = diskIO;
        this.mainThread = mainThread;
    }

    public static AppExecutors getInstance() {
        if (sInstance == null) {
            synchronized (LOCK) {
                if (sInstance == null) {
                    // Use a single thread executor for sequential DB access
                    sInstance = new AppExecutors(Executors.newSingleThreadExecutor(),
                            new MainThreadExecutor());
                }
            }
        }
        return sInstance;
    }

    // Executor for background tasks (database, network)
    public Executor diskIO() {
        return diskIO;
    }

    // Executor for posting results to the main thread
    public Executor mainThread() {
        return mainThread;
    }

    private static class MainThreadExecutor implements Executor {
        private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(@NonNull Runnable command) {
            mainThreadHandler.post(command);
        }
    }
}