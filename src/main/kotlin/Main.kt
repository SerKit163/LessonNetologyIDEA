import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dto.Author
import dto.Comment
import dto.Post
import dto.PostWithComments
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private const val BASE_URL = "http://127.0.0.1:9999"
private const val URL_POSTS = "api/slow/posts"

private val gson = Gson()

private val client = OkHttpClient.Builder()
    .addInterceptor(HttpLoggingInterceptor(::println).apply {
        level = HttpLoggingInterceptor.Level.BODY
    })
    .connectTimeout(30, TimeUnit.SECONDS)
    .build()


fun main(args: Array<String>) {

    with(CoroutineScope(EmptyCoroutineContext)) {
        launch {
            try {
                val posts = getPosts(client)
                    .map { post ->
                        async {
                            PostWithComments(post, getComments(client, post.id))
                        }

                    }.awaitAll()
//                println("===================================")
//                println(posts)
//                println("===================================")
                println()

                for (i in posts) {
                    val post = i.post

                    val authorPostDeferred = async {
                        getAuthor(client, i.post.authorId)
                    }

                    val authorCommentsDeferred = i.comments.map { comment ->
                        async {
                            getAuthor(client, comment.authorId)
                        }
                    }

                    val authorPost = authorPostDeferred.await()
                    val authorComments = authorCommentsDeferred.awaitAll()

//                    val authorPost = async { getAuthor(client, i.post.authorId) }.await()

//                    НЕ правильно
//                    val authorComment = if (!i.comments.isEmpty()) getAuthor(client, i.comments[0].authorId).let { author ->
//                        async {
//                            author
//                        }
//                    }.await() else null

                    println()
                    println("##################################")
                    println("Автор поста: ${authorPost.name}")
                    println("\t${post.content}")
                    println("----------------------------------")

                    i.comments.forEach { comment ->
                        println("<<< КОММЕНТАРИЙ >>>")
                        println("Автор комментария: ${authorComments.find { 
                            it.id == comment.authorId
                        }?.name ?: ""}")
                        println("\t${comment.content}")
                        println("----------------------------------")
                    }
                    println()




//                    for (c in i.comments) {
//                        val commentA = async { getAuthor(client, c.authorId) }.await()
//
//                        println("Author Comment: ${commentA.name}")
//                        println("\t${c.content}")
//                    }

//                    println(if (authorComment != null) "Автор комментария: ${authorComment.name}" else "")
//                    println("\t$commentsContent")
//                    println("----------------------------------")
//                    println()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    Thread.sleep(30_000L)

}

suspend fun OkHttpClient.apiCall(url: String): Response {
    return suspendCoroutine { continuation ->
        Request.Builder()
            .url(url)
            .build()
            .let(::newCall)
            .enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }


            })
    }
}

suspend fun <T> makeRequest(url: String, client: OkHttpClient, typeToken: TypeToken<T>): T =
    withContext(Dispatchers.IO) {
        client.apiCall(url)
            .let { response ->
                if (!response.isSuccessful) {
                    response.close()
                    throw RuntimeException(response.message)
                }
                val body = response.body ?: throw RuntimeException("response body is null")
                gson.fromJson(body.string(), typeToken.type)
            }
    }

suspend fun getPosts(client: OkHttpClient): List<Post> =
    makeRequest("$BASE_URL/$URL_POSTS/", client, object : TypeToken<List<Post>>() {})

suspend fun getComments(client: OkHttpClient, id: Long): List<Comment> =
    makeRequest("$BASE_URL/$URL_POSTS/$id/comments", client, object : TypeToken<List<Comment>>() {})

suspend fun getAuthor(client: OkHttpClient, authorId: Long): Author =
    makeRequest("$BASE_URL/api/authors/$authorId", client, object : TypeToken<Author>() {})