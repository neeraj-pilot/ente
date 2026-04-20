package io.ente.entegram.core.crypto

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import uniffi.ente_ffi.FfiCreatePostImage
import uniffi.ente_ffi.FfiWallSession
import uniffi.ente_ffi.wallApproveFollowRequest
import uniffi.ente_ffi.wallCancelFollowRequest
import uniffi.ente_ffi.wallCreateCommentJson
import uniffi.ente_ffi.wallCreateJson
import uniffi.ente_ffi.wallCreatePostJson
import uniffi.ente_ffi.wallDeleteComment
import uniffi.ente_ffi.wallDeletePost
import uniffi.ente_ffi.wallFetchPostJson
import uniffi.ente_ffi.wallListCommentsJson
import uniffi.ente_ffi.wallListFeedJson
import uniffi.ente_ffi.wallListFollowRequestsJson
import uniffi.ente_ffi.wallListFollowersJson
import uniffi.ente_ffi.wallListFollowingJson
import uniffi.ente_ffi.wallListOwnedWallsJson
import uniffi.ente_ffi.wallListWallPostsJson
import uniffi.ente_ffi.wallLoadAssetBytes
import uniffi.ente_ffi.wallLoadAvatarBytes
import uniffi.ente_ffi.wallLookupBySlugJson
import uniffi.ente_ffi.wallRemoveAvatarJson
import uniffi.ente_ffi.wallRejectFollowRequest
import uniffi.ente_ffi.wallRequestFollow
import uniffi.ente_ffi.wallSearchCommunityJson
import uniffi.ente_ffi.wallSetPostLike
import uniffi.ente_ffi.wallUnfollow
import uniffi.ente_ffi.wallUpdateProfileJson
import uniffi.ente_ffi.wallUploadAvatarJson

interface EnteWallCore {
    fun listOwnedWallsJson(session: FfiWallSession): String
    fun lookupBySlugJson(session: FfiWallSession, wallSlug: String): String
    fun createWallJson(
        session: FfiWallSession,
        wallSlug: String,
        displayName: String?,
        bio: String?,
    ): String
    fun updateWallProfileJson(
        session: FfiWallSession,
        wallId: String,
        displayName: String?,
        bio: String?,
    ): String
    fun uploadAvatarJson(session: FfiWallSession, wallId: String, jpegData: ByteArray): String
    fun removeAvatarJson(session: FfiWallSession, wallId: String): String
    fun listWallPostsJson(
        session: FfiWallSession,
        wallId: String,
        cursor: String?,
        limit: Int,
    ): String
    fun listFeedJson(session: FfiWallSession, cursor: String?, limit: Int): String
    fun fetchPostJson(session: FfiWallSession, postId: Long): String
    fun createPostJson(
        session: FfiWallSession,
        wallId: String,
        caption: String,
        images: List<FfiCreatePostImage>,
    ): String
    fun setPostLike(session: FfiWallSession, postId: Long, like: Boolean): Boolean
    fun deletePost(session: FfiWallSession, postId: Long)
    fun listCommentsJson(
        session: FfiWallSession,
        postId: Long,
        postKeyB64: String?,
        cursor: String?,
        limit: Int,
    ): String
    fun createCommentJson(
        session: FfiWallSession,
        postId: Long,
        postKeyB64: String,
        text: String,
        parentCommentId: Long?,
    ): String
    fun deleteComment(session: FfiWallSession, postId: Long, commentId: Long)
    fun searchCommunityJson(
        session: FfiWallSession,
        query: String,
        cursor: String?,
        limit: Int,
    ): String
    fun listFollowRequestsJson(session: FfiWallSession, direction: String): String
    fun listFollowersJson(session: FfiWallSession, wallId: String): String
    fun listFollowingJson(session: FfiWallSession): String
    fun requestFollow(session: FfiWallSession, wallId: String)
    fun approveFollowRequest(session: FfiWallSession, requestId: Long)
    fun rejectFollowRequest(session: FfiWallSession, requestId: Long)
    fun cancelFollowRequest(session: FfiWallSession, requestId: Long)
    fun unfollow(session: FfiWallSession, wallId: String)
    fun loadAssetBytes(
        session: FfiWallSession,
        wallId: String,
        objectKey: String,
        postKeyB64: String,
    ): ByteArray
    fun loadAvatarBytes(
        session: FfiWallSession,
        wallId: String,
        objectKey: String,
    ): ByteArray
}

@Singleton
class UniFfiEnteWallCore @Inject constructor() : EnteWallCore {
    override fun listOwnedWallsJson(session: FfiWallSession): String = wallListOwnedWallsJson(session)

    override fun lookupBySlugJson(session: FfiWallSession, wallSlug: String): String =
        wallLookupBySlugJson(session, wallSlug)

    override fun createWallJson(
        session: FfiWallSession,
        wallSlug: String,
        displayName: String?,
        bio: String?,
    ): String = wallCreateJson(session, wallSlug, displayName, bio)

    override fun updateWallProfileJson(
        session: FfiWallSession,
        wallId: String,
        displayName: String?,
        bio: String?,
    ): String = wallUpdateProfileJson(session, wallId, displayName, bio)

    override fun uploadAvatarJson(
        session: FfiWallSession,
        wallId: String,
        jpegData: ByteArray,
    ): String = wallUploadAvatarJson(session, wallId, jpegData)

    override fun removeAvatarJson(
        session: FfiWallSession,
        wallId: String,
    ): String = wallRemoveAvatarJson(session, wallId)

    override fun listWallPostsJson(
        session: FfiWallSession,
        wallId: String,
        cursor: String?,
        limit: Int,
    ): String = wallListWallPostsJson(session, wallId, cursor, limit)

    override fun listFeedJson(session: FfiWallSession, cursor: String?, limit: Int): String =
        wallListFeedJson(session, cursor, limit)

    override fun fetchPostJson(session: FfiWallSession, postId: Long): String =
        wallFetchPostJson(session, postId)

    override fun createPostJson(
        session: FfiWallSession,
        wallId: String,
        caption: String,
        images: List<FfiCreatePostImage>,
    ): String = wallCreatePostJson(session, wallId, caption, images)

    override fun setPostLike(session: FfiWallSession, postId: Long, like: Boolean): Boolean =
        wallSetPostLike(session, postId, like)

    override fun deletePost(session: FfiWallSession, postId: Long) {
        wallDeletePost(session, postId)
    }

    override fun listCommentsJson(
        session: FfiWallSession,
        postId: Long,
        postKeyB64: String?,
        cursor: String?,
        limit: Int,
    ): String = wallListCommentsJson(session, postId, postKeyB64, cursor, limit)

    override fun createCommentJson(
        session: FfiWallSession,
        postId: Long,
        postKeyB64: String,
        text: String,
        parentCommentId: Long?,
    ): String = wallCreateCommentJson(session, postId, postKeyB64, text, parentCommentId)

    override fun deleteComment(session: FfiWallSession, postId: Long, commentId: Long) {
        wallDeleteComment(session, postId, commentId)
    }

    override fun searchCommunityJson(
        session: FfiWallSession,
        query: String,
        cursor: String?,
        limit: Int,
    ): String = wallSearchCommunityJson(session, query, cursor, limit)

    override fun listFollowRequestsJson(session: FfiWallSession, direction: String): String =
        wallListFollowRequestsJson(session, direction)

    override fun listFollowersJson(session: FfiWallSession, wallId: String): String =
        wallListFollowersJson(session, wallId)

    override fun listFollowingJson(session: FfiWallSession): String =
        wallListFollowingJson(session)

    override fun requestFollow(session: FfiWallSession, wallId: String) {
        wallRequestFollow(session, wallId)
    }

    override fun approveFollowRequest(session: FfiWallSession, requestId: Long) {
        wallApproveFollowRequest(session, requestId)
    }

    override fun rejectFollowRequest(session: FfiWallSession, requestId: Long) {
        wallRejectFollowRequest(session, requestId)
    }

    override fun cancelFollowRequest(session: FfiWallSession, requestId: Long) {
        wallCancelFollowRequest(session, requestId)
    }

    override fun unfollow(session: FfiWallSession, wallId: String) {
        wallUnfollow(session, wallId)
    }

    override fun loadAssetBytes(
        session: FfiWallSession,
        wallId: String,
        objectKey: String,
        postKeyB64: String,
    ): ByteArray = wallLoadAssetBytes(session, wallId, objectKey, postKeyB64)

    override fun loadAvatarBytes(
        session: FfiWallSession,
        wallId: String,
        objectKey: String,
    ): ByteArray = wallLoadAvatarBytes(session, wallId, objectKey)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EnteWallCoreModule {
    @Binds
    @Singleton
    abstract fun bindEnteWallCore(
        impl: UniFfiEnteWallCore,
    ): EnteWallCore
}
