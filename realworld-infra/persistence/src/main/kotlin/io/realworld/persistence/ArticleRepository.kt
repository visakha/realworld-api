package io.realworld.persistence

import arrow.core.ForOption
import arrow.core.Option
import arrow.core.fix
import arrow.core.getOrElse
import arrow.core.some
import arrow.core.toOption
import arrow.effects.IO
import arrow.instances.extensions
import arrow.typeclasses.binding
import io.realworld.domain.articles.Article
import io.realworld.domain.articles.ValidArticleCreation
import io.realworld.domain.articles.ValidArticleUpdate
import io.realworld.domain.profiles.Profile
import io.realworld.domain.users.User
import io.realworld.persistence.ArticleTbl.eq
import org.springframework.dao.support.DataAccessUtils
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class ArticleRow(
  val id: UUID,
  val slug: String,
  val title: String,
  val description: String,
  val body: String,
  val authorId: UUID,
  val createdAt: Instant,
  val updatedAt: Instant
) {
  companion object {
    fun fromRs(rs: ResultSet) = with(ArticleTbl) {
      ArticleRow(
        id = UUID.fromString(rs.getString(id)),
        slug = rs.getString(slug),
        title = rs.getString(title),
        description = rs.getString(description),
        body = rs.getString(body),
        authorId = UUID.fromString(rs.getString(author)),
        createdAt = rs.getTimestamp(created_at).toInstant(),
        updatedAt = rs.getTimestamp(updated_at).toInstant()
      )
    }
  }
}

data class ArticleDeps(
  val tagList: MutableList<String> = mutableListOf(),
  var author: Profile? = null,
  var favorited: Boolean = false,
  var favoritesCount: Long = 0L
)

private fun Article.Companion.from(row: ArticleRow, deps: ArticleDeps) = Article(
  id = row.id,
  slug = row.slug,
  title = row.title,
  description = row.description,
  body = row.body,
  createdAt = row.createdAt,
  updatedAt = row.updatedAt,

  tagList = deps.tagList,
  favoritesCount = deps.favoritesCount,
  favorited = deps.favorited,
  author = deps.author!!
)

class ArticleRepository(
  val jdbcTemplate: NamedParameterJdbcTemplate,
  val userRepo: UserRepository
) {

  fun create(article: ValidArticleCreation, user: User): IO<Article> {
    return IO {
      val row = insertArticleRow(article, user)

      val deps = ArticleDeps()
      deps.author = Profile(
        username = user.username,
        bio = user.bio.toOption(),
        image = user.image.toOption(),
        following = false.toOption()
      )

      if (article.tagList.isNotEmpty()) {
        insertTags(article.tagList)
        insertArticleTags(article.id, article.tagList)
        deps.tagList.addAll(article.tagList)
      }

      Article.from(row, deps)
    }
  }

  fun existsBySlug(slug: String): IO<Boolean> = ArticleTbl.let {
    jdbcTemplate.queryIfExists(it.table, "${it.slug.eq()}", mapOf(it.slug to slug))
  }

  fun getBySlug(slug: String, user: Option<User>): IO<Option<Article>> = IO {
    ForOption extensions {
      binding {
        val row = fetchRowBySlug(slug).bind()

        val deps = ArticleDeps()
        deps.tagList.addAll(fetchArticleTags(row.id))
        deps.author = fetchAuthor(row.authorId, user)

        Article.from(row, deps)
      }.fix()
    }
  }

  fun deleteArticle(articleId: UUID): IO<Int> = with(ArticleTbl) {
    val sql = "DELETE FROM $table WHERE ${id.eq()}"
    val params = mapOf(id to articleId)
    IO {
      jdbcTemplate.update(sql, params)
    }
  }

  fun updateArticle(update: ValidArticleUpdate, user: User): IO<Article> {
    return IO {
      val row = updateArticleRow(update)

      val deps = ArticleDeps()
      deps.tagList.addAll(fetchArticleTags(row.id))
      deps.author = fetchAuthor(row.authorId, user.some())

      Article.from(row, deps)
    }
  }

  private fun updateArticleRow(update: ValidArticleUpdate): ArticleRow = with(ArticleTbl) {
    val sql =
      """
      UPDATE $table
      SET
        ${slug.set()},
        ${title.set()},
        ${description.set()},
        ${body.set()},
        $updated_at = CURRENT_TIMESTAMP
      WHERE
        ${id.eq()}
      RETURNING *
      """
    val params = mapOf(
      slug to update.slug,
      title to update.title,
      description to update.description,
      body to update.body,
      id to update.id
    )
    jdbcTemplate.queryForObject(sql, params, { rs, _ -> ArticleRow.fromRs (rs) })!!
  }

  private fun fetchArticleTags(articleId: UUID): List<String> = with(ArticleTagTbl) {
    val sql = "SELECT $tag FROM $table WHERE ${article_id.eq()}"
    val params = mapOf(article_id to articleId)
    jdbcTemplate.query(sql, params, { rs, _ -> rs.getString(tag) })
  }

  private fun fetchRowBySlug(slug: String): Option<ArticleRow> = ArticleTbl.let {
    val sql = "SELECT * FROM ${it.table} WHERE ${it.slug.eq()}"
    val params = mapOf(it.slug to slug)
    DataAccessUtils.singleResult(
      jdbcTemplate.query(sql, params, { rs, _ -> ArticleRow.fromRs(rs) })
    ).toOption()
  }

  private fun fetchAuthor(id: UUID, querier: Option<User>): Profile =
    userRepo.findById(id).unsafeRunSync().map {
      with(it.user) {
        Profile(
          username = username,
          bio = bio.toOption(),
          image = image.toOption(),
          following = querier.map { userRepo.hasFollower(username, it.username).unsafeRunSync() }
        )
      }
    }.getOrElse { throw RuntimeException("Corrupt DB: article author $id not found") }

  private fun insertArticleRow(article: ValidArticleCreation, user: User): ArticleRow = with(ArticleTbl) {
    val u = UserTbl
    val sql =
      """
      INSERT INTO $table (
        $id, $slug, $title, $description, $body, $author
      ) VALUES (
        :$id, :$slug, :$title, :$description, :$body,
        (SELECT ${u.id} FROM ${u.table} WHERE ${u.username} = :authorUsername)
      )
      RETURNING *
      """
    val params = mapOf(
        id to article.id,
        slug to article.slug,
        title to article.title,
        description to article.description,
        body to article.body,
        "authorUsername" to user.username
    )
    jdbcTemplate.queryForObject(
      sql,
      params,
      { rs, _ -> ArticleRow.fromRs(rs) }
    )!!
  }

  private fun insertTags(tags: List<String>) = with(TagTbl) {
    val sql = "INSERT INTO $table ($name) VALUES (:$name) ON CONFLICT ($name) DO NOTHING"
    val params = tags.map { mapOf(name to it) }.toTypedArray()
    jdbcTemplate.batchUpdate(sql, params)
  }

  private fun insertArticleTags(articleId: UUID, tags: List<String>) = with(ArticleTagTbl) {
    val sql = "INSERT INTO $table ($article_id, $tag) VALUES (:$article_id, :$tag)"
    val params = tags.map {
      mapOf(
        article_id to articleId,
        tag to it
      )
    }.toTypedArray()
    jdbcTemplate.batchUpdate(sql, params)
  }
}
