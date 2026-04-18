/*
 * Copyright 2014-2025 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.aeron.samples.agent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight async event logger for the sample agent. This is the reusable piece you can keep
 * when instrumenting different business classes.
 */
public final class AgentLogRecorder
{
    private static final BlockingQueue<String> EVENTS = new LinkedBlockingQueue<>();
    private static final AtomicBoolean IS_RUNNING = new AtomicBoolean(false);
    private static volatile Thread readerThread;

    private AgentLogRecorder()
    {
    }

    public static void start()
    {
        if (!IS_RUNNING.compareAndSet(false, true))
        {
            return;
        }

        final Thread thread = new Thread(() ->
        {
            while (IS_RUNNING.get() || !EVENTS.isEmpty())
            {
                try
                {
                    final String event = EVENTS.poll(100, TimeUnit.MILLISECONDS);
                    if (null != event)
                    {
                        System.out.println("[agent] " + event);
                    }
                }
                catch (final InterruptedException ignore)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });

        thread.setName("sample-agent-reader");
        thread.setDaemon(true);
        thread.start();
        readerThread = thread;
    }

    public static void stop()
    {
        if (!IS_RUNNING.compareAndSet(true, false))
        {
            return;
        }

        final Thread thread = readerThread;
        if (null != thread)
        {
            thread.interrupt();
        }
    }

    public static void record(final String event)
    {
        EVENTS.offer(event);
    }
}
