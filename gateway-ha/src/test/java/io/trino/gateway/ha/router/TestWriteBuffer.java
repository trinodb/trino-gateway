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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestWriteBuffer
{
    @Test
    void testBufferDropsOldestWhenFull()
    {
        WriteBuffer<Integer> buffer = new WriteBuffer<>(2);
        buffer.buffer(1);
        buffer.buffer(2);
        // At capacity now. Next add should drop 1 (oldest)
        buffer.buffer(3);
        assertThat(buffer.size()).isEqualTo(2);

        List<Integer> flushed = new ArrayList<>();
        int flushedCount = buffer.flushAll(flushed::add);
        assertThat(flushedCount).isEqualTo(2);
        assertThat(flushed).containsExactly(2, 3);
    }

    @Test
    void testFlushAllRequeue()
    {
        WriteBuffer<Integer> buffer = new WriteBuffer<>(10);
        buffer.buffer(1);
        buffer.buffer(2);
        buffer.buffer(3);

        List<Integer> processed = new ArrayList<>();
        int flushedCount = buffer.flushAll(i -> {
            if (i == 2) {
                throw new RuntimeException("fail on 2");
            }
            processed.add(i);
        });

        // Only '1' should be processed, '2' fails and is requeued at head, '3' not attempted
        assertThat(flushedCount).isEqualTo(1);
        assertThat(processed).containsExactly(1);
        assertThat(buffer.size()).isEqualTo(2);

        // Next flush with a no-op flusher should process [2, 3]
        List<Integer> retried = new ArrayList<>();
        int retriedCount = buffer.flushAll(retried::add);
        assertThat(retriedCount).isEqualTo(2);
        assertThat(retried).containsExactly(2, 3);
        assertThat(buffer.size()).isZero();
    }
}
