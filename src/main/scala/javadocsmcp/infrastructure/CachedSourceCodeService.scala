// PURPOSE: Caching decorator for SourceCodeService using LRU cache
// PURPOSE: Improves performance by caching source code lookup results

package javadocsmcp.infrastructure

import javadocsmcp.domain.{SourceCode, DocumentationError}

class CachedSourceCodeService(
  underlying: javadocsmcp.application.SourceCodeService,
  cache: LRUCache[CacheKey, Either[DocumentationError, String]]
):
  def getSource(
    coordinatesStr: String,
    classNameStr: String,
    scalaVersion: Option[String] = None
  ): Either[DocumentationError, SourceCode] = {
    val effectiveScalaVersion = scalaVersion.getOrElse("3")
    val key = CacheKey(coordinatesStr, classNameStr, effectiveScalaVersion)

    // Check cache first
    cache.get(key) match
      case Some(cachedResult) =>
        // Cache hit - return cached result, wrapping in SourceCode if successful
        cachedResult.flatMap { sourceContent =>
          // We need to parse className and coords again to construct SourceCode
          // This is acceptable since cache hit avoids expensive network/JAR operations
          for {
            className <- javadocsmcp.domain.ClassName.parse(classNameStr)
            coords <- javadocsmcp.domain.ArtifactCoordinates.parse(coordinatesStr)
          } yield SourceCode(sourceContent, className, coords)
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
  ): Either[DocumentationError, SourceCode] = {
    val result = underlying.getSource(coordinatesStr, classNameStr, scalaVersion)

    // Cache the source content (or error)
    val cacheableResult = result.map(_.sourceText)
    cache.put(key, cacheableResult)

    result
  }

object CachedSourceCodeService:
  def apply(
    underlying: javadocsmcp.application.SourceCodeService,
    cache: LRUCache[CacheKey, Either[DocumentationError, String]]
  ): CachedSourceCodeService =
    new CachedSourceCodeService(underlying, cache)
