package com.touchkiss.pastetomd.preview

import kotlin.test.*

class PreviewCopyRequestTest {
    @Test
    fun `large image preparation has time beyond the native clipboard timeout`() {
        var time = 0L
        val requests = PreviewCopyRequest { time }
        val id = assertNotNull(requests.start())
        time = 4_000_000_000L
        assertTrue(requests.finish(id, true))
    }

    @Test
    fun `conversion can inspect a pending request without consuming it`() {
        var time = 0L
        val requests = PreviewCopyRequest { time }
        val first = assertNotNull(requests.start())
        assertTrue(requests.isPending(first))
        time += 200_000_000L
        val next = assertNotNull(requests.start())
        assertFalse(requests.isPending(first))
        assertFalse(requests.finish(first, true))
        assertTrue(requests.isPending(next))
        assertFalse(requests.finish(next, false))
        assertFalse(requests.isPending(next))
    }

    @Test
    fun `conversion crossing deadline cannot commit and timeout remains reportable`() {
        var time = 0L
        val requests = PreviewCopyRequest { time }
        val id = assertNotNull(requests.start())
        assertTrue(requests.isPending(id))
        time = 30_000_000_000L
        assertFalse(requests.isPending(id))
        assertFalse(requests.finish(id, true))
        assertTrue(requests.expire(id))
        assertFalse(requests.finish(id, true))
    }

    @Test
    fun `only latest request can commit once`() {
        var time = 0L
        val requests = PreviewCopyRequest { time }
        val first = assertNotNull(requests.start())
        time += 200_000_000L
        val second = assertNotNull(requests.start())
        assertFalse(requests.finish(first, true))
        assertTrue(requests.finish(second, true))
        assertFalse(requests.finish(second, true))
    }

    @Test
    fun `duplicate browser and IDE key dispatches are coalesced`() {
        var time = 0L
        val requests = PreviewCopyRequest { time }
        assertNotNull(requests.start())
        time += 50_000_000L
        assertNull(requests.start())
        time += 100_000_000L
        assertNotNull(requests.start())
    }

    @Test
    fun `expired canceled and invalid context responses never commit`() {
        var time = 0L
        val requests = PreviewCopyRequest { time }
        val expired = assertNotNull(requests.start())
        time = 30_000_000_000L
        assertFalse(requests.finish(expired, true))
        val switched = assertNotNull(requests.start())
        assertFalse(requests.finish(switched, false))
        time += 200_000_000L
        val canceled = assertNotNull(requests.start())
        requests.cancel()
        assertFalse(requests.finish(canceled, true))
        time += 200_000_000L
        val timedOut = assertNotNull(requests.start())
        assertTrue(requests.expire(timedOut))
        assertFalse(requests.finish(timedOut, true))
    }

    @Test
    fun `old timeout does not cancel a new request`() {
        var time = 0L
        val requests = PreviewCopyRequest { time }
        val first = assertNotNull(requests.start())
        time += 200_000_000L
        val second = assertNotNull(requests.start())
        assertFalse(requests.expire(first))
        assertTrue(requests.finish(second, true))
    }
}
