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
package io.aeron.samples.bytebuddy;

import io.aeron.Aeron;
import io.aeron.FragmentAssembler;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.samples.SampleConfiguration;
import org.agrona.BufferUtil;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Self-contained Aeron sample that publishes a few messages and gives the Byte Buddy agent
 * one obvious method to intercept.
 */
public final class InterceptedBasicPublisher
{
    private static final int STREAM_ID = SampleConfiguration.STREAM_ID;
    private static final String CHANNEL = SampleConfiguration.CHANNEL;

    private final Publication publication;
    private final UnsafeBuffer buffer = new UnsafeBuffer(BufferUtil.allocateDirectAligned(256, 64));

    InterceptedBasicPublisher(final Publication publication)
    {
        this.publication = publication;
    }

    public static void main(final String[] args)
    {
        System.out.println("Running Byte Buddy Aeron sample on " + CHANNEL + " stream " + STREAM_ID);

        try (MediaDriver driver = MediaDriver.launchEmbedded();
            Aeron aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()));
            Subscription subscription = aeron.addSubscription(CHANNEL, STREAM_ID);
            Publication publication = aeron.addPublication(CHANNEL, STREAM_ID))
        {
            waitForConnection(publication, subscription);

            final InterceptedBasicPublisher publisher = new InterceptedBasicPublisher(publication);
            final FragmentAssembler assembler = new FragmentAssembler(
                (buffer, offset, length, header) ->
                    System.out.println("Subscriber received: " + buffer.getStringWithoutLengthAscii(offset, length)));

            for (long messageIndex = 0; messageIndex < 5; messageIndex++)
            {
                final long result = publisher.publishMessage(messageIndex);
                System.out.println("Application saw offer result " + result);

                while (subscription.poll(assembler, 10) == 0)
                {
                    Thread.yield();
                }
            }
        }
    }

    public long publishMessage(final long messageIndex)
    {
        final int length = buffer.putStringWithoutLengthAscii(0, "Hello from Byte Buddy message " + messageIndex);
        return publication.offer(buffer, 0, length);
    }

    private static void waitForConnection(final Publication publication, final Subscription subscription)
    {
        while (!publication.isConnected() || !subscription.isConnected())
        {
            Thread.yield();
        }
    }
}
