// PURPOSE: Caching decorator for DocumentationService using LRU cache
// PURPOSE: Improves performance by caching documentation lookup results

package javadocsmcp.infrastructure

import javadocsmcp.domain.{Documentation, DocumentationError}

case class CacheKey(
  coordinates: String,
  className: String,
  scalaVersion: String
)

class CachedDocumentationService(
  underlying: javadocsmcp.application.DocumentationService,
  cache: LRUCache[CacheKey, Either[DocumentationError, String]]
):
  def getDocumentation(
    coordinatesStr: String,
    classNameStr: String,
    scalaVersion: Option[String] = None
  ): Either[DocumentationError, Documentation] = {
    val effectiveScalaVersion = scalaVersion.getOrElse("3")
    val key = CacheKey(coordinatesStr, classNameStr, effectiveScalaVersion)

    // Check cache first
    cache.get(key) match
      case Some(cachedResult) =>
        // Cache hit - return cached result, wrapping in Documentation if successful
        cachedResult.flatMap { htmlContent =>
          // We need to parse className and coords again to construct Documentation
          // This is acceptable since cache hit avoids expensive network/JAR operations
          for {
            className <- javadocsmcp.domain.ClassName.parse(classNameStr)
            coords <- javadocsmcp.domain.ArtifactCoordinates.parse(coordinatesStr)
          } yield Documentation(htmlContent, className.fullyQualifiedName, coords)
        }

      case None =>
        // Cache miss - fetch from underlying service and cache result
        getFromUnderlying(coordinatesStr, classNameStr, scalaVersion, key)
  }

  private def getFromUnderlying(
    coordinatesStr: String,
    classNameStr: String,
    scalaVersion: Option[String],
    key: CacheKey
  ): Either[DocumentationError, Documentation] = {
    val result = underlying.getDocumentation(coordinatesStr, classNameStr, scalaVersion)

    // Cache the HTML content (or error)
    val cacheableResult = result.map(_.htmlContent)
    cache.put(key, cacheableResult)

    result
  }

object CachedDocumentationService:
  def apply(
    underlying: javadocsmcp.application.DocumentationService,
    cache: LRUCache[CacheKey, Either[DocumentationError, String]]
  ): CachedDocumentationService =
    new CachedDocumentationService(underlying, cache)
