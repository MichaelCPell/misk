package misk.hibernate

import io.opentracing.mock.MockTracer
import misk.resources.ResourceLoader
import misk.testing.MiskTest
import misk.testing.MiskTestModule
import org.junit.jupiter.api.Test
import javax.inject.Inject

@MiskTest(startService = true)
internal class VitessSchemaMigratorTest {
  @MiskTestModule
  val module = MoviesTestModule()

  @Inject @Movies lateinit var transacter: Transacter
  @Inject lateinit var queryFactory: Query.Factory
  @Inject lateinit var tracer: MockTracer
  @Inject lateinit var resourceLoader: ResourceLoader
  @Inject @Movies lateinit var schemaMigrator: SchemaMigrator

  @Test fun availableMigrations() {
    schemaMigrator.availableMigrations(Keyspace("actors"))
  }
}
