package io.ente.entegram.core.services

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MockWallClientTest {

    @Test
    fun `listFeed returns stable ordering across calls`() = runTest {
        val client = MockWallClient()

        val first = client.listFeed(cursor = null, limit = 20)
        val second = client.listFeed(cursor = null, limit = 20)

        assertEquals(first.items.map { it.id }, second.items.map { it.id })
        assertTrue(first.items.size >= 9, "Expected at least 9 sample posts")
    }

    @Test
    fun `listFeed returns posts in reverse chronological order`() = runTest {
        val client = MockWallClient()
        val page = client.listFeed(cursor = null, limit = 20)

        val timestamps = page.items.map { it.createdAt }
        assertEquals(timestamps, timestamps.sortedDescending())
    }

    @Test
    fun `pagination returns disjoint pages with valid cursors`() = runTest {
        val client = MockWallClient()

        val page1 = client.listFeed(cursor = null, limit = 3)
        assertEquals(3, page1.items.size)
        assertNotNull(page1.nextCursor)

        val page2 = client.listFeed(cursor = page1.nextCursor, limit = 3)
        assertEquals(3, page2.items.size)

        val ids1 = page1.items.map { it.id }.toSet()
        val ids2 = page2.items.map { it.id }.toSet()
        assertTrue(ids1.intersect(ids2).isEmpty(), "Pages should not overlap")
    }

    @Test
    fun `likePost toggles viewerLiked and increments count`() = runTest {
        val client = MockWallClient()
        val postId = 1001L

        val before = client.fetchPost(postId)
        assertEquals(false, before.viewerLiked)
        val originalCount = before.likeCount

        client.likePost(postId)

        val after = client.fetchPost(postId)
        assertEquals(true, after.viewerLiked)
        assertEquals(originalCount + 1, after.likeCount)
    }

    @Test
    fun `unlikePost toggles viewerLiked and decrements count`() = runTest {
        val client = MockWallClient()

        client.likePost(1001)
        client.unlikePost(1001)

        val post = client.fetchPost(1001)
        assertEquals(false, post.viewerLiked)
    }

    @Test
    fun `createComment increments post commentCount`() = runTest {
        val client = MockWallClient()
        val postId = 1003L

        val before = client.fetchPost(postId)
        val comment = client.createComment(postId, parentId = null, text = "nice map!")
        val after = client.fetchPost(postId)

        assertEquals(before.commentCount + 1, after.commentCount)
        assertEquals("nice map!", comment.text)
        assertEquals(postId, comment.postId)
        assertNull(comment.parentId)
    }

    @Test
    fun `listFollowRequests filters by direction`() = runTest {
        val client = MockWallClient()

        val incoming = client.listFollowRequests(io.ente.entegram.core.models.FollowRequest.Direction.Incoming)
        val outgoing = client.listFollowRequests(io.ente.entegram.core.models.FollowRequest.Direction.Outgoing)

        assertTrue(incoming.all { it.direction == io.ente.entegram.core.models.FollowRequest.Direction.Incoming })
        assertTrue(outgoing.all { it.direction == io.ente.entegram.core.models.FollowRequest.Direction.Outgoing })
        assertEquals(2, incoming.size)
        assertEquals(1, outgoing.size)
    }

    @Test
    fun `approveFollowRequest removes it from the list`() = runTest {
        val client = MockWallClient()

        val before = client.listFollowRequests(io.ente.entegram.core.models.FollowRequest.Direction.Incoming)
        assertEquals(2, before.size)

        client.approveFollowRequest(before.first().id)

        val after = client.listFollowRequests(io.ente.entegram.core.models.FollowRequest.Direction.Incoming)
        assertEquals(1, after.size)
    }

    @Test
    fun `loadAssetBytes returns an in-memory placeholder payload`() = runTest {
        val client = MockWallClient()

        val bytes1 = client.loadAssetBytes("mock/lisbon-light")
        val bytes2 = client.loadAssetBytes("mock/lisbon-light")

        assertTrue(bytes1.contentEquals(bytes2))
        assertTrue(bytes1.isEmpty())
    }

    @Test
    fun `searchCommunity filters by slug and displayName`() = runTest {
        val client = MockWallClient()

        val results = client.searchCommunity("lena", limit = 10)
        assertTrue(results.isNotEmpty())
        assertTrue(results.all {
            it.slug.contains("lena", ignoreCase = true) ||
                (it.displayName?.contains("lena", ignoreCase = true) == true)
        })
    }
}
