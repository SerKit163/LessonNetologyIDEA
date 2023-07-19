package dto

data class Comment(
    val id: Long,
    val postId: Long,
    val authorId: Long,
    val content: String,
    val published: Long,
    val likedById: Boolean,
    val likes: Int = 0
)
