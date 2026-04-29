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

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import java.util.Map;
import java.util.TreeMap;

import static io.aeron.samples.cluster.hydra.HydraExchangeCodec.*;

/**
 * Deterministic, in-memory state for the sample exchange service.
 */
final class HydraOrderBook
{
    static final int SNAPSHOT_COUNT_OFFSET = 0;
    static final int SNAPSHOT_ENTRY_OFFSET = SNAPSHOT_COUNT_OFFSET + BitUtil.SIZE_OF_INT;
    static final int SNAPSHOT_ORDER_ID_OFFSET = 0;
    static final int SNAPSHOT_ACCOUNT_ID_OFFSET = SNAPSHOT_ORDER_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    static final int SNAPSHOT_SIDE_OFFSET = SNAPSHOT_ACCOUNT_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    static final int SNAPSHOT_PRICE_OFFSET = SNAPSHOT_SIDE_OFFSET + BitUtil.SIZE_OF_BYTE;
    static final int SNAPSHOT_QUANTITY_OFFSET = SNAPSHOT_PRICE_OFFSET + BitUtil.SIZE_OF_LONG;
    static final int SNAPSHOT_ENTRY_LENGTH = SNAPSHOT_QUANTITY_OFFSET + BitUtil.SIZE_OF_LONG;

    private final TreeMap<Long, Order> ordersById = new TreeMap<>();

    CommandResult newOrder(
        final long correlationId,
        final long orderId,
        final long accountId,
        final byte side,
        final long price,
        final long quantity)
    {
        if ((SIDE_BUY != side && SIDE_SELL != side) || orderId <= 0 || accountId <= 0 || price <= 0 || quantity <= 0)
        {
            return rejected(correlationId, orderId, accountId, REASON_BAD_REQUEST);
        }

        if (ordersById.containsKey(orderId))
        {
            return rejected(correlationId, orderId, accountId, REASON_DUPLICATE_ORDER);
        }

        ordersById.put(orderId, new Order(orderId, accountId, side, price, quantity));

        return accepted(correlationId, orderId, accountId);
    }

    CommandResult cancelOrder(final long correlationId, final long orderId, final long accountId)
    {
        final Order order = ordersById.get(orderId);
        if (null == order || order.accountId != accountId)
        {
            return rejected(correlationId, orderId, accountId, REASON_UNKNOWN_ORDER);
        }

        ordersById.remove(orderId);

        return accepted(correlationId, orderId, accountId);
    }

    int snapshotTo(final MutableDirectBuffer buffer)
    {
        buffer.putInt(SNAPSHOT_COUNT_OFFSET, ordersById.size());

        int offset = SNAPSHOT_ENTRY_OFFSET;
        for (final Order order : ordersById.values())
        {
            buffer.putLong(offset + SNAPSHOT_ORDER_ID_OFFSET, order.orderId);
            buffer.putLong(offset + SNAPSHOT_ACCOUNT_ID_OFFSET, order.accountId);
            buffer.putByte(offset + SNAPSHOT_SIDE_OFFSET, order.side);
            buffer.putLong(offset + SNAPSHOT_PRICE_OFFSET, order.price);
            buffer.putLong(offset + SNAPSHOT_QUANTITY_OFFSET, order.quantity);
            offset += SNAPSHOT_ENTRY_LENGTH;
        }

        return offset;
    }

    void loadSnapshot(final DirectBuffer buffer, final int offset)
    {
        ordersById.clear();

        final int count = buffer.getInt(offset + SNAPSHOT_COUNT_OFFSET);
        int entryOffset = offset + SNAPSHOT_ENTRY_OFFSET;

        for (int i = 0; i < count; i++)
        {
            final long orderId = buffer.getLong(entryOffset + SNAPSHOT_ORDER_ID_OFFSET);
            final long accountId = buffer.getLong(entryOffset + SNAPSHOT_ACCOUNT_ID_OFFSET);
            final byte side = buffer.getByte(entryOffset + SNAPSHOT_SIDE_OFFSET);
            final long price = buffer.getLong(entryOffset + SNAPSHOT_PRICE_OFFSET);
            final long quantity = buffer.getLong(entryOffset + SNAPSHOT_QUANTITY_OFFSET);

            ordersById.put(orderId, new Order(orderId, accountId, side, price, quantity));
            entryOffset += SNAPSHOT_ENTRY_LENGTH;
        }
    }

    int orderCount()
    {
        return ordersById.size();
    }

    long bestBid()
    {
        long bestBid = 0;
        for (final Order order : ordersById.values())
        {
            if (SIDE_BUY == order.side && order.price > bestBid)
            {
                bestBid = order.price;
            }
        }

        return bestBid;
    }

    long bestAsk()
    {
        long bestAsk = 0;
        for (final Order order : ordersById.values())
        {
            if (SIDE_SELL == order.side && (0 == bestAsk || order.price < bestAsk))
            {
                bestAsk = order.price;
            }
        }

        return bestAsk;
    }

    private CommandResult accepted(final long correlationId, final long orderId, final long accountId)
    {
        return new CommandResult(correlationId, orderId, accountId, STATUS_ACCEPTED, REASON_OK, bestBid(), bestAsk());
    }

    private CommandResult rejected(
        final long correlationId,
        final long orderId,
        final long accountId,
        final byte reason)
    {
        return new CommandResult(correlationId, orderId, accountId, STATUS_REJECTED, reason, bestBid(), bestAsk());
    }

    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("HydraOrderBook{orders=").append(ordersById.size());
        builder.append(", bestBid=").append(bestBid());
        builder.append(", bestAsk=").append(bestAsk());
        builder.append(", ordersById=[");

        for (final Map.Entry<Long, Order> entry : ordersById.entrySet())
        {
            builder.append(entry.getValue()).append(',');
        }

        if (!ordersById.isEmpty())
        {
            builder.setLength(builder.length() - 1);
        }

        return builder.append("]}").toString();
    }

    static final class CommandResult
    {
        final long correlationId;
        final long orderId;
        final long accountId;
        final byte status;
        final byte reason;
        final long bestBid;
        final long bestAsk;

        CommandResult(
            final long correlationId,
            final long orderId,
            final long accountId,
            final byte status,
            final byte reason,
            final long bestBid,
            final long bestAsk)
        {
            this.correlationId = correlationId;
            this.orderId = orderId;
            this.accountId = accountId;
            this.status = status;
            this.reason = reason;
            this.bestBid = bestBid;
            this.bestAsk = bestAsk;
        }
    }

    private static final class Order
    {
        final long orderId;
        final long accountId;
        final byte side;
        final long price;
        final long quantity;

        Order(final long orderId, final long accountId, final byte side, final long price, final long quantity)
        {
            this.orderId = orderId;
            this.accountId = accountId;
            this.side = side;
            this.price = price;
            this.quantity = quantity;
        }

        public String toString()
        {
            return "Order{" +
                "orderId=" + orderId +
                ", accountId=" + accountId +
                ", side=" + sideAsString(side) +
                ", price=" + price +
                ", quantity=" + quantity +
                '}';
        }
    }
}
