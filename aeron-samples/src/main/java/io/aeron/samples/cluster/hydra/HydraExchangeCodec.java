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

/**
 * A tiny hand-written stand-in for the generated codecs/proxies that Hydra-style projects normally use.
 */
final class HydraExchangeCodec
{
    static final byte NEW_ORDER = 1;
    static final byte CANCEL_ORDER = 2;
    static final byte COMMAND_RESULT = 3;

    static final byte SIDE_BUY = 1;
    static final byte SIDE_SELL = 2;

    static final byte STATUS_ACCEPTED = 1;
    static final byte STATUS_REJECTED = 2;

    static final byte REASON_OK = 0;
    static final byte REASON_DUPLICATE_ORDER = 1;
    static final byte REASON_UNKNOWN_ORDER = 2;
    static final byte REASON_BAD_REQUEST = 3;

    static final int TYPE_OFFSET = 0;
    static final int CORRELATION_ID_OFFSET = TYPE_OFFSET + BitUtil.SIZE_OF_BYTE;
    static final int ORDER_ID_OFFSET = CORRELATION_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    static final int ACCOUNT_ID_OFFSET = ORDER_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    static final int SIDE_OFFSET = ACCOUNT_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    static final int PRICE_OFFSET = SIDE_OFFSET + BitUtil.SIZE_OF_BYTE;
    static final int QUANTITY_OFFSET = PRICE_OFFSET + BitUtil.SIZE_OF_LONG;
    static final int NEW_ORDER_LENGTH = QUANTITY_OFFSET + BitUtil.SIZE_OF_LONG;

    static final int CANCEL_ORDER_LENGTH = ACCOUNT_ID_OFFSET + BitUtil.SIZE_OF_LONG;

    static final int RESULT_STATUS_OFFSET = ACCOUNT_ID_OFFSET + BitUtil.SIZE_OF_LONG;
    static final int RESULT_REASON_OFFSET = RESULT_STATUS_OFFSET + BitUtil.SIZE_OF_BYTE;
    static final int RESULT_BEST_BID_OFFSET = RESULT_REASON_OFFSET + BitUtil.SIZE_OF_BYTE;
    static final int RESULT_BEST_ASK_OFFSET = RESULT_BEST_BID_OFFSET + BitUtil.SIZE_OF_LONG;
    static final int COMMAND_RESULT_LENGTH = RESULT_BEST_ASK_OFFSET + BitUtil.SIZE_OF_LONG;

    private HydraExchangeCodec()
    {
    }

    static int encodeNewOrder(
        final MutableDirectBuffer buffer,
        final long correlationId,
        final long orderId,
        final long accountId,
        final byte side,
        final long price,
        final long quantity)
    {
        buffer.putByte(TYPE_OFFSET, NEW_ORDER);
        buffer.putLong(CORRELATION_ID_OFFSET, correlationId);
        buffer.putLong(ORDER_ID_OFFSET, orderId);
        buffer.putLong(ACCOUNT_ID_OFFSET, accountId);
        buffer.putByte(SIDE_OFFSET, side);
        buffer.putLong(PRICE_OFFSET, price);
        buffer.putLong(QUANTITY_OFFSET, quantity);

        return NEW_ORDER_LENGTH;
    }

    static int encodeCancelOrder(
        final MutableDirectBuffer buffer,
        final long correlationId,
        final long orderId,
        final long accountId)
    {
        buffer.putByte(TYPE_OFFSET, CANCEL_ORDER);
        buffer.putLong(CORRELATION_ID_OFFSET, correlationId);
        buffer.putLong(ORDER_ID_OFFSET, orderId);
        buffer.putLong(ACCOUNT_ID_OFFSET, accountId);

        return CANCEL_ORDER_LENGTH;
    }

    static int encodeResult(
        final MutableDirectBuffer buffer,
        final long correlationId,
        final long orderId,
        final long accountId,
        final byte status,
        final byte reason,
        final long bestBid,
        final long bestAsk)
    {
        buffer.putByte(TYPE_OFFSET, COMMAND_RESULT);
        buffer.putLong(CORRELATION_ID_OFFSET, correlationId);
        buffer.putLong(ORDER_ID_OFFSET, orderId);
        buffer.putLong(ACCOUNT_ID_OFFSET, accountId);
        buffer.putByte(RESULT_STATUS_OFFSET, status);
        buffer.putByte(RESULT_REASON_OFFSET, reason);
        buffer.putLong(RESULT_BEST_BID_OFFSET, bestBid);
        buffer.putLong(RESULT_BEST_ASK_OFFSET, bestAsk);

        return COMMAND_RESULT_LENGTH;
    }

    static String sideAsString(final byte side)
    {
        return SIDE_BUY == side ? "BUY" : SIDE_SELL == side ? "SELL" : "UNKNOWN";
    }

    static String statusAsString(final byte status)
    {
        return STATUS_ACCEPTED == status ? "ACCEPTED" : "REJECTED";
    }

    static String reasonAsString(final byte reason)
    {
        switch (reason)
        {
            case REASON_OK:
                return "OK";

            case REASON_DUPLICATE_ORDER:
                return "DUPLICATE_ORDER";

            case REASON_UNKNOWN_ORDER:
                return "UNKNOWN_ORDER";

            case REASON_BAD_REQUEST:
                return "BAD_REQUEST";

            default:
                return "UNKNOWN_REASON";
        }
    }

    static byte type(final DirectBuffer buffer, final int offset)
    {
        return buffer.getByte(offset + TYPE_OFFSET);
    }
}
