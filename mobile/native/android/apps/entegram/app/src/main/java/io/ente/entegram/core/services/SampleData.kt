package io.ente.entegram.core.services

import io.ente.entegram.core.models.AspectPreset
import io.ente.entegram.core.models.AuthenticatedUser
import io.ente.entegram.core.models.LoginResult
import io.ente.entegram.core.models.CaptionPayload
import io.ente.entegram.core.models.Comment
import io.ente.entegram.core.models.CommunityResult
import io.ente.entegram.core.models.FollowRequest
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.PostAsset
import io.ente.entegram.core.models.PostAssetVariant
import io.ente.entegram.core.models.Wall
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Canonical seed data for previews + mock client.
 * Ported from iOS `SampleData.swift` — same users, slugs, captions.
 */
object SampleData {

    // ── Viewer ──────────────────────────────────────────────────

    val viewer = AuthenticatedUser(
        id = 42,
        email = "me@example.com",
        username = null,
        sessionToken = "mock-session-token",
    )

    val viewerLoginResult = LoginResult(
        user = viewer,
        masterKey = ByteArray(32) { it.toByte() },
        secretKey = ByteArray(32) { (it + 32).toByte() },
        publicKey = ByteArray(32) { (it + 64).toByte() },
    )

    // ── Walls ───────────────────────────────────────────────────

    private val now: Instant get() = Instant.now()

    val lena = Wall(
        id = "wall-lena",
        slug = "lena",
        displayName = "Lena Marchetti",
        bio = "Shutter therapy from a small apartment in Lisbon.",
        avatarObjectKey = null,
        keyVersion = 1,
        createdAt = now.minus(90, ChronoUnit.DAYS),
        followerCount = 184,
        followingCount = 73,
        postCount = 37,
    )

    val soraKitchen = Wall(
        id = "wall-sora",
        slug = "sora-kitchen",
        displayName = "Sora's Kitchen",
        bio = "weeknight dinners \u00b7 pantry alchemy",
        avatarObjectKey = null,
        keyVersion = 1,
        createdAt = now.minus(180, ChronoUnit.DAYS),
        followerCount = 92,
        followingCount = 41,
        postCount = 58,
    )

    val mapmaker = Wall(
        id = "wall-mapmaker",
        slug = "mapmaker",
        displayName = "Henrik",
        bio = "walking every street in the city. slowly.",
        avatarObjectKey = null,
        keyVersion = 1,
        createdAt = now.minus(365, ChronoUnit.DAYS),
        followerCount = 1_203,
        followingCount = 312,
        postCount = 412,
    )

    val ivoryArchive = Wall(
        id = "wall-ivory",
        slug = "ivory-archive",
        displayName = "The Ivory Archive",
        bio = "daily page of the illuminated manuscript project",
        avatarObjectKey = null,
        keyVersion = 1,
        createdAt = now.minus(220, ChronoUnit.DAYS),
        followerCount = 41,
        followingCount = 19,
        postCount = 74,
    )

    val viewerWall = Wall(
        id = "wall-you",
        slug = "you",
        displayName = "Me",
        bio = "",
        avatarObjectKey = null,
        keyVersion = 1,
        createdAt = now.minus(14, ChronoUnit.DAYS),
        followerCount = 2,
        followingCount = 1,
        postCount = 4,
    )

    val walls: List<Wall> = listOf(lena, soraKitchen, mapmaker, ivoryArchive, viewerWall)

    // ── Photographic seeds (deterministic Picsum URLs) ──────────

    val photographicSeeds = listOf(
        "lisbon-light", "miso-bowl", "eggplant-glaze", "street-map",
        "manuscript-84v", "tram-28-a", "tram-28-b", "tram-28-c",
        "pomelo", "kreuzberg-line", "gold-leaf", "testing-wall",
        "sunset-rooftop", "herbs-garden", "rain-walk", "folio-91",
        "alley-morning", "rice-bowl", "neighborhood-sign", "ink-detail",
        "bridge-dusk", "spice-rack", "park-bench", "vellum-edge",
        "harbor-light", "noodle-steam", "cobblestone", "margin-hare",
        "tile-pattern", "pantry-shelf",
    )

    // ── Posts ────────────────────────────────────────────────────

    private fun asset(
        position: Int,
        seed: String,
        aspect: AspectPreset = AspectPreset.Portrait,
    ): PostAsset = PostAsset(
        position = position,
        variant = PostAssetVariant.Full,
        objectKey = "mock/$seed",
        blurHash = "LEHV6nWB2yk8pyoJadR*.7kCMdnj",
        aspect = aspect,
    )

    val posts: List<Post> = listOf(
        Post(
            id = 1001,
            wallId = lena.id,
            authorSlug = lena.slug,
            authorDisplayName = lena.displayName,
            createdAt = now.minus(2, ChronoUnit.HOURS),
            caption = CaptionPayload(
                text = "Rooftop light, 6:47pm. Nothing else to say.",
                images = listOf(
                    CaptionPayload.Entry(0, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Portrait),
                ),
            ),
            assets = listOf(asset(0, "lisbon-light")),
            likeCount = 23,
            commentCount = 1,
            viewerLiked = false,
        ),
        Post(
            id = 1002,
            wallId = soraKitchen.id,
            authorSlug = soraKitchen.slug,
            authorDisplayName = soraKitchen.displayName,
            createdAt = now.minus(5, ChronoUnit.HOURS),
            caption = CaptionPayload(
                text = "Miso-glazed eggplant, black rice, soft herbs. 20 min pantry meal, recipe in the reply.",
                images = listOf(
                    CaptionPayload.Entry(0, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Square),
                    CaptionPayload.Entry(1, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Square),
                ),
            ),
            assets = listOf(
                asset(0, "miso-bowl", AspectPreset.Square),
                asset(1, "eggplant-glaze", AspectPreset.Square),
            ),
            likeCount = 47,
            commentCount = 3,
            viewerLiked = true,
        ),
        Post(
            id = 1003,
            wallId = mapmaker.id,
            authorSlug = mapmaker.slug,
            authorDisplayName = mapmaker.displayName,
            createdAt = now.minus(8, ChronoUnit.HOURS),
            caption = CaptionPayload(
                text = "Finished the SW quadrant today. 62 streets on foot this month, 19km of it in rain.",
                images = listOf(
                    CaptionPayload.Entry(0, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Landscape),
                ),
            ),
            assets = listOf(asset(0, "street-map", AspectPreset.Landscape)),
            likeCount = 112,
            commentCount = 0,
            viewerLiked = false,
        ),
        Post(
            id = 1004,
            wallId = ivoryArchive.id,
            authorSlug = ivoryArchive.slug,
            authorDisplayName = ivoryArchive.displayName,
            createdAt = now.minus(12, ChronoUnit.HOURS),
            caption = CaptionPayload(
                text = "Folio 84v \u2014 marginalia of a hare holding a trumpet. No one knows why.",
                images = listOf(
                    CaptionPayload.Entry(0, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Portrait),
                ),
            ),
            assets = listOf(asset(0, "manuscript-84v")),
            likeCount = 8,
            commentCount = 1,
            viewerLiked = false,
        ),
        Post(
            id = 1005,
            wallId = lena.id,
            authorSlug = lena.slug,
            authorDisplayName = lena.displayName,
            createdAt = now.minus(1, ChronoUnit.DAYS),
            caption = CaptionPayload(
                text = "Tram 28 never misses.",
                images = listOf(
                    CaptionPayload.Entry(0, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Landscape),
                    CaptionPayload.Entry(1, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Portrait),
                    CaptionPayload.Entry(2, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Square),
                ),
            ),
            assets = listOf(
                asset(0, "tram-28-a", AspectPreset.Landscape),
                asset(1, "tram-28-b"),
                asset(2, "tram-28-c", AspectPreset.Square),
            ),
            likeCount = 61,
            commentCount = 2,
            viewerLiked = true,
        ),
        Post(
            id = 1006,
            wallId = soraKitchen.id,
            authorSlug = soraKitchen.slug,
            authorDisplayName = soraKitchen.displayName,
            createdAt = now.minus(2, ChronoUnit.DAYS),
            caption = CaptionPayload(
                text = "Found this pomelo at the corner store. It does not fit the fridge.",
                images = listOf(
                    CaptionPayload.Entry(0, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Square),
                ),
            ),
            assets = listOf(asset(0, "pomelo", AspectPreset.Square)),
            likeCount = 34,
            commentCount = 0,
            viewerLiked = false,
        ),
        Post(
            id = 1007,
            wallId = mapmaker.id,
            authorSlug = mapmaker.slug,
            authorDisplayName = mapmaker.displayName,
            createdAt = now.minus(3, ChronoUnit.DAYS),
            caption = CaptionPayload(
                text = "Parish line between Kreuzberg and Neuk\u00f6lln. Still not sure it\u2019s the one on the map.",
                images = listOf(
                    CaptionPayload.Entry(0, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Landscape),
                ),
            ),
            assets = listOf(asset(0, "kreuzberg-line", AspectPreset.Landscape)),
            likeCount = 89,
            commentCount = 0,
            viewerLiked = false,
        ),
        Post(
            id = 1008,
            wallId = ivoryArchive.id,
            authorSlug = ivoryArchive.slug,
            authorDisplayName = ivoryArchive.displayName,
            createdAt = now.minus(4, ChronoUnit.DAYS),
            caption = CaptionPayload(
                text = "Gold leaf day. The camera does not catch the glow; it only hints at it.",
                images = listOf(
                    CaptionPayload.Entry(0, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Portrait),
                ),
            ),
            assets = listOf(asset(0, "gold-leaf")),
            likeCount = 15,
            commentCount = 0,
            viewerLiked = false,
        ),
        Post(
            id = 2001,
            wallId = viewerWall.id,
            authorSlug = viewerWall.slug,
            authorDisplayName = viewerWall.displayName,
            createdAt = now.minus(6, ChronoUnit.DAYS),
            caption = CaptionPayload(
                text = "testing my wall",
                images = listOf(
                    CaptionPayload.Entry(0, "LEHV6nWB2yk8pyoJadR*.7kCMdnj", AspectPreset.Square),
                ),
            ),
            assets = listOf(asset(0, "testing-wall", AspectPreset.Square)),
            likeCount = 1,
            commentCount = 0,
            viewerLiked = false,
        ),
    )

    // ── Comments ────────────────────────────────────────────────

    val comments: List<Comment> = listOf(
        Comment(
            id = 5001,
            postId = 1002,
            parentId = null,
            authorSlug = lena.slug,
            authorDisplayName = lena.displayName,
            text = "Recipe drop when",
            createdAt = now.minus(4, ChronoUnit.HOURS),
            replyCount = 1,
        ),
        Comment(
            id = 5002,
            postId = 1002,
            parentId = 5001,
            authorSlug = soraKitchen.slug,
            authorDisplayName = soraKitchen.displayName,
            text = "tonight \uD83E\uDEE1",
            createdAt = now.minus(3, ChronoUnit.HOURS),
            replyCount = 0,
        ),
        Comment(
            id = 5003,
            postId = 1002,
            parentId = null,
            authorSlug = mapmaker.slug,
            authorDisplayName = mapmaker.displayName,
            text = "the plate is at a perfect 30\u00b0 incline, i respect this",
            createdAt = now.minus(4, ChronoUnit.HOURS),
            replyCount = 0,
        ),
        Comment(
            id = 5004,
            postId = 1005,
            parentId = null,
            authorSlug = mapmaker.slug,
            authorDisplayName = mapmaker.displayName,
            text = "the third shot \u2014 is that Gra\u00e7a?",
            createdAt = now.minus(22, ChronoUnit.HOURS),
            replyCount = 1,
        ),
        Comment(
            id = 5005,
            postId = 1005,
            parentId = 5004,
            authorSlug = lena.slug,
            authorDisplayName = lena.displayName,
            text = "sharp eye, yeah \u2014 just below the miradouro",
            createdAt = now.minus(21, ChronoUnit.HOURS),
            replyCount = 0,
        ),
        Comment(
            id = 5006,
            postId = 1001,
            parentId = null,
            authorSlug = ivoryArchive.slug,
            authorDisplayName = ivoryArchive.displayName,
            text = "that light is the whole post",
            createdAt = now.minus(1, ChronoUnit.HOURS),
            replyCount = 0,
        ),
    )

    // ── Follow requests ─────────────────────────────────────────

    val followRequests: List<FollowRequest> = listOf(
        FollowRequest(
            id = 9001,
            fromUserId = 100,
            fromSlug = "pascal",
            fromDisplayName = "Pascal",
            wallId = viewerWall.id,
            createdAt = now.minus(8, ChronoUnit.HOURS),
            direction = FollowRequest.Direction.Incoming,
        ),
        FollowRequest(
            id = 9002,
            fromUserId = 101,
            fromSlug = "junebird",
            fromDisplayName = "June",
            wallId = viewerWall.id,
            createdAt = now.minus(1, ChronoUnit.DAYS),
            direction = FollowRequest.Direction.Incoming,
        ),
        FollowRequest(
            id = 9003,
            fromUserId = 42,
            fromSlug = "teo",
            fromDisplayName = "Teo Ogawa",
            wallId = "wall-teo",
            createdAt = now.minus(2, ChronoUnit.DAYS),
            direction = FollowRequest.Direction.Outgoing,
        ),
    )

    // ── Community search fixtures ───────────────────────────────

    val communityResults: List<CommunityResult> = walls
        .filter { it.slug != viewerWall.slug }
        .map { wall ->
            CommunityResult(
                id = wall.id,
                slug = wall.slug,
                displayName = wall.displayName,
                followerCount = wall.followerCount,
                relationship = null,
            )
        }
}
