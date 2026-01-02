// PURPOSE: Unit tests for TastySourceResolver
// PURPOSE: Tests TASTy-based source path resolution logic

package javadocsmcp.infrastructure

class TastySourceResolverTest extends munit.FunSuite:

  test("extract package-relative path from project-relative path"):
    val projectPath = "core/shared/src/main/scala/cats/effect/IO.scala"
    val packageName = "cats.effect"

    val result = TastySourceResolver.extractPackageRelativePath(projectPath, packageName)

    assertEquals(result, "cats/effect/IO.scala")

  test("extract package-relative path when path contains nested directories"):
    val projectPath = "modules/core/jvm/src/main/scala/zio/ZIO.scala"
    val packageName = "zio"

    val result = TastySourceResolver.extractPackageRelativePath(projectPath, packageName)

    assertEquals(result, "zio/ZIO.scala")

  test("fallback to filename only when package path not found"):
    val projectPath = "some/weird/path/MyClass.scala"
    val packageName = "com.example"

    val result = TastySourceResolver.extractPackageRelativePath(projectPath, packageName)

    assertEquals(result, "MyClass.scala")

  test("handle deeply nested package"):
    val projectPath = "src/main/scala/com/example/deep/nested/pkg/Service.scala"
    val packageName = "com.example.deep.nested.pkg"

    val result = TastySourceResolver.extractPackageRelativePath(projectPath, packageName)

    assertEquals(result, "com/example/deep/nested/pkg/Service.scala")

  test("handle root package (empty package name)"):
    val projectPath = "src/main/scala/RootClass.scala"
    val packageName = ""

    val result = TastySourceResolver.extractPackageRelativePath(projectPath, packageName)

    // With empty package, we can't find a package path, so fallback to filename
    assertEquals(result, "RootClass.scala")
