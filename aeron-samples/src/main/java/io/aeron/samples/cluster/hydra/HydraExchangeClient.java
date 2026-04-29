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
package io.aeron.samples.cluster.hydra;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.aeron.cluster.codecs.EventCode;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

import java.util.Arrays;
import java.util.List;

import static io.aeron.samples.cluster.hydra.HydraExchangeCodec.*;
import static io.aeron.samples.cluster.hydra.HydraExchangeClusterNode.calculatePort;

/**
 * Client that submits sample order flow to the Hydra-style exchange cluster.
 */
public final class HydraExchangeClient implements EgressListener
{
    private final MutableDirectBuffer commandBuffer = new ExpandableArrayBuffer();
    private final IdleStrategy idleStrategy = new BackoffIdleStrategy();
    private final long accountId;
    private final int commandCount;

    private long nextCorrelationId = 1;
    private int responses;

    private HydraExchangeClient(final long accountId, final int commandCount)
    {
        this.accountId = accountId;
        this.commandCount = commandCount;
    }

    /**
     * {@inheritDoc}
     */
    public void onMessage(
        final long clusterSessionId,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        final long correlationId = buffer.getLong(offset + CORRELATION_ID_OFFSET);
        final long orderId = buffer.getLong(offset + ORDER_ID_OFFSET);
        final byte status = buffer.getByte(offset + RESULT_STATUS_OFFSET);
        final byte reason = buffer.getByte(offset + RESULT_REASON_OFFSET);
        final long bestBid = buffer.getLong(offset + RESULT_BEST_BID_OFFSET);
        final long bestAsk = buffer.getLong(offset + RESULT_BEST_ASK_OFFSET);

        responses++;
        System.out.println(
            "egress session=" + clusterSessionId +
            " correlationId=" + correlationId +
            " orderId=" + orderId +
            " status=" + statusAsString(status) +
            " reason=" + reasonAsString(reason) +
            " bestBid=" + bestBid +
            " bestAsk=" + bestAsk);
    }

    /**
     * {@inheritDoc}
     */
    public void onSessionEvent(
        final long correlationId,
        final long clusterSessionId,
        final long leadershipTermId,
        final int leaderMemberId,
        final EventCode code,
        final String detail)
    {
        System.out.println(
            "session event correlationId=" + correlationId +
            " leadershipTermId=" + leadershipTermId +
            " leaderMemberId=" + leaderMemberId +
            " code=" + code +
            " detail=" + detail);
    }

    /**
     * {@inheritDoc}
     */
    public void onNewLeader(
        final long clusterSessionId,
        final long leadershipTermId,
        final int leaderMemberId,
        final String ingressEndpoints)
    {
        System.out.println(
            "new leader session=" + clusterSessionId +
            " leadershipTermId=" + leadershipTermId +
            " leaderMemberId=" + leaderMemberId);
    }

    private void run(final AeronCluster aeronCluster)
    {
        for (int i = 0; i < commandCount; i++)
        {
            if (i > 0 && 0 == i % 4)
            {
                final long orderIdToCancel = accountId * 1_000 + i - 1;
                sendCancelOrder(aeronCluster, orderIdToCancel);
            }
            else
            {
                final byte side = 0 == i % 2 ? SIDE_BUY : SIDE_SELL;
                final long orderId = accountId * 1_000 + i;
                final long price = SIDE_BUY == side ? 100_00 + (i * 25L) : 101_00 + (i * 25L);
                sendNewOrder(aeronCluster, orderId, side, price, 10 + i);
            }

            awaitEgress(aeronCluster);
        }
    }

    private void sendNewOrder(
        final AeronCluster aeronCluster,
        final long orderId,
        final byte side,
        final long price,
        final long quantity)
    {
        final long correlationId = nextCorrelationId++;
        final int encodedLength = encodeNewOrder(
            commandBuffer, correlationId, orderId, accountId, side, price, quantity);

        offer(aeronCluster, encodedLength);
        System.out.println(
            "sent NEW_ORDER correlationId=" + correlationId +
            " orderId=" + orderId +
            " side=" + sideAsString(side) +
            " price=" + price +
            " quantity=" + quantity);
    }

    private void sendCancelOrder(final AeronCluster aeronCluster, final long orderId)
    {
        final long correlationId = nextCorrelationId++;
        final int encodedLength = encodeCancelOrder(commandBuffer, correlationId, orderId, accountId);

        offer(aeronCluster, encodedLength);
        System.out.println("sent CANCEL_ORDER correlationId=" + correlationId + " orderId=" + orderId);
    }

    private void offer(final AeronCluster aeronCluster, final int encodedLength)
    {
        idleStrategy.reset();
        while (aeronCluster.offer(commandBuffer, 0, encodedLength) < 0)
        {
            idleStrategy.idle(aeronCluster.pollEgress());
        }
    }

    private void awaitEgress(final AeronCluster aeronCluster)
    {
        final int targetResponses = responses + 1;
        idleStrategy.reset();

        while (responses < targetResponses)
        {
            idleStrategy.idle(aeronCluster.pollEgress());
            aeronCluster.sendKeepAlive();
        }
    }

    static String ingressEndpoints(final List<String> hostnames)
    {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hostnames.size(); i++)
        {
            sb.append(i).append('=');
            sb.append(hostnames.get(i)).append(':').append(
                calculatePort(i, HydraExchangeClusterNode.CLIENT_FACING_PORT_OFFSET));
            sb.append(',');
        }

        sb.setLength(sb.length() - 1);

        return sb.toString();
    }

    /**
     * Main method for launching the client.
     *
     * @param args passed to the process.
     */
    public static void main(final String[] args)
    {
        final long accountId = Long.getLong("aeron.cluster.hydra.accountId", 42);
        final int commandCount = Integer.getInteger("aeron.cluster.hydra.commandCount", 12);
        final String[] hostnames =
            System.getProperty("aeron.cluster.hydra.hostnames", "localhost,localhost,localhost").split(",");

        final HydraExchangeClient client = new HydraExchangeClient(accountId, commandCount);

        try (MediaDriver mediaDriver = MediaDriver.launchEmbedded(new MediaDriver.Context()
            .threadingMode(ThreadingMode.SHARED)
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true));
            AeronCluster aeronCluster = AeronCluster.connect(
                new AeronCluster.Context()
                .egressListener(client)
                .egressChannel("aeron:udp?endpoint=localhost:0")
                .aeronDirectoryName(mediaDriver.aeronDirectoryName())
                .ingressChannel("aeron:udp")
                .ingressEndpoints(ingressEndpoints(Arrays.asList(hostnames)))))
        {
            client.run(aeronCluster);
        }
    }
}
