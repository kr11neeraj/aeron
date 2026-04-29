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

import io.aeron.ExclusivePublication;
import io.aeron.Image;
import io.aeron.cluster.codecs.CloseReason;
import io.aeron.cluster.service.ClientSession;
import io.aeron.cluster.service.Cluster;
import io.aeron.cluster.service.ClusteredService;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.MutableBoolean;
import org.agrona.concurrent.IdleStrategy;

import static io.aeron.samples.cluster.hydra.HydraExchangeCodec.*;

/**
 * Clustered exchange service that mirrors the architecture shown in Aeron/Hydra exchange talks.
 */
public final class HydraExchangeClusteredService implements ClusteredService
{
    private final HydraOrderBook orderBook = new HydraOrderBook();
    private final MutableDirectBuffer egressBuffer = new ExpandableArrayBuffer();
    private final MutableDirectBuffer snapshotBuffer = new ExpandableArrayBuffer();

    private Cluster cluster;
    private IdleStrategy idleStrategy;

    /**
     * {@inheritDoc}
     */
    public void onStart(final Cluster cluster, final Image snapshotImage)
    {
        this.cluster = cluster;
        this.idleStrategy = cluster.idleStrategy();

        if (null != snapshotImage)
        {
            loadSnapshot(snapshotImage);
        }

        System.out.println("Hydra exchange service started with " + orderBook.orderCount() + " live orders");
    }

    /**
     * {@inheritDoc}
     */
    public void onSessionMessage(
        final ClientSession session,
        final long timestamp,
        final DirectBuffer buffer,
        final int offset,
        final int length,
        final Header header)
    {
        final HydraOrderBook.CommandResult result;
        final byte messageType = type(buffer, offset);

        switch (messageType)
        {
            case NEW_ORDER:
                result = onNewOrder(buffer, offset, length);
                break;

            case CANCEL_ORDER:
                result = onCancelOrder(buffer, offset, length);
                break;

            default:
                result = new HydraOrderBook.CommandResult(
                    0, 0, 0, STATUS_REJECTED, REASON_BAD_REQUEST, orderBook.bestBid(), orderBook.bestAsk());
                break;
        }

        System.out.println("cluster timestamp=" + timestamp + " result=" + statusAsString(result.status) +
            " orderId=" + result.orderId + " bestBid=" + result.bestBid + " bestAsk=" + result.bestAsk);

        if (null != session)
        {
            final int encodedLength = encodeResult(
                egressBuffer,
                result.correlationId,
                result.orderId,
                result.accountId,
                result.status,
                result.reason,
                result.bestBid,
                result.bestAsk);

            idleStrategy.reset();
            while (session.offer(egressBuffer, 0, encodedLength) < 0)
            {
                idleStrategy.idle();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void onTakeSnapshot(final ExclusivePublication snapshotPublication)
    {
        final int length = orderBook.snapshotTo(snapshotBuffer);

        idleStrategy.reset();
        while (snapshotPublication.offer(snapshotBuffer, 0, length) < 0)
        {
            idleStrategy.idle();
        }

        System.out.println("snapshot taken " + orderBook);
    }

    private HydraOrderBook.CommandResult onNewOrder(
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        if (length < NEW_ORDER_LENGTH)
        {
            return new HydraOrderBook.CommandResult(
                0, 0, 0, STATUS_REJECTED, REASON_BAD_REQUEST, orderBook.bestBid(), orderBook.bestAsk());
        }

        return orderBook.newOrder(
            buffer.getLong(offset + CORRELATION_ID_OFFSET),
            buffer.getLong(offset + ORDER_ID_OFFSET),
            buffer.getLong(offset + ACCOUNT_ID_OFFSET),
            buffer.getByte(offset + SIDE_OFFSET),
            buffer.getLong(offset + PRICE_OFFSET),
            buffer.getLong(offset + QUANTITY_OFFSET));
    }

    private HydraOrderBook.CommandResult onCancelOrder(
        final DirectBuffer buffer,
        final int offset,
        final int length)
    {
        if (length < CANCEL_ORDER_LENGTH)
        {
            return new HydraOrderBook.CommandResult(
                0, 0, 0, STATUS_REJECTED, REASON_BAD_REQUEST, orderBook.bestBid(), orderBook.bestAsk());
        }

        return orderBook.cancelOrder(
            buffer.getLong(offset + CORRELATION_ID_OFFSET),
            buffer.getLong(offset + ORDER_ID_OFFSET),
            buffer.getLong(offset + ACCOUNT_ID_OFFSET));
    }

    private void loadSnapshot(final Image snapshotImage)
    {
        final MutableBoolean loaded = new MutableBoolean(false);

        while (!snapshotImage.isEndOfStream())
        {
            final int fragments = snapshotImage.poll(
                (buffer, offset, length, header) ->
                {
                    orderBook.loadSnapshot(buffer, offset);
                    loaded.set(true);
                },
                1);

            if (loaded.value)
            {
                break;
            }

            idleStrategy.idle(fragments);
        }

        System.out.println("snapshot loaded " + orderBook);
    }

    /**
     * {@inheritDoc}
     */
    public void onRoleChange(final Cluster.Role newRole)
    {
        System.out.println("role changed to " + newRole);
    }

    /**
     * {@inheritDoc}
     */
    public void onTerminate(final Cluster cluster)
    {
        System.out.println("terminating Hydra exchange service");
    }

    /**
     * {@inheritDoc}
     */
    public void onSessionOpen(final ClientSession session, final long timestamp)
    {
        System.out.println("session opened " + session.id() + " at " + timestamp);
    }

    /**
     * {@inheritDoc}
     */
    public void onSessionClose(final ClientSession session, final long timestamp, final CloseReason closeReason)
    {
        System.out.println("session closed " + session.id() + " reason=" + closeReason + " at " + timestamp);
    }

    /**
     * {@inheritDoc}
     */
    public void onTimerEvent(final long correlationId, final long timestamp)
    {
    }
}
