/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.gateway.ha.router;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

public final class WriteBuffer<T>
{
    private final BlockingDeque<T> deque;

    public WriteBuffer(int maxCapacity)
    {
        this.deque = new LinkedBlockingDeque<>(maxCapacity);
    }

    /**
     * Buffer an item for later flush. Drops the oldest item if the buffer is full.
     *
     * @return true if an existing item was dropped to make room, false otherwise
     */
    public synchronized boolean buffer(T item)
    {
        if (!deque.offerLast(item)) {
            deque.pollFirst();
            deque.offerLast(item);
            return true;
        }
        return false;
    }

    /**
     * Flushes items in insertion order by applying the provided flusher.
     * Stops immediately if the flusher throws, leaving the failed item at the head.
     *
     * @param flusher consumer invoked for each buffered item
     * @return number of items successfully flushed
     */
    public synchronized int flushAll(Consumer<T> flusher)
    {
        int flushed = 0;
        for (T next; (next = deque.peekFirst()) != null; ) {
            try {
                flusher.accept(next);
            }
            catch (RuntimeException e) {
                break; // stop after first failure
            }
            // Only remove after a successful flush
            deque.pollFirst();
            flushed++;
        }
        return flushed;
    }

    public synchronized int size()
    {
        return deque.size();
    }
}
