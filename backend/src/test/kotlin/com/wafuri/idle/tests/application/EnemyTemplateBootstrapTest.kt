package com.wafuri.idle.tests.application

import com.wafuri.idle.application.service.enemy.DatabaseEnemyFetcher
import com.wafuri.idle.application.service.enemy.EnemyTemplateBootstrap
import com.wafuri.idle.application.service.enemy.EnemyTemplateCatalog
import com.wafuri.idle.application.service.enemy.ResourceEnemyFetcher
import com.wafuri.idle.tests.support.strawGolemEnemy
import com.wafuri.idle.tests.support.trainingDummyEnemy
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import jakarta.enterprise.inject.Instance

class EnemyTemplateBootstrapTest :
  StringSpec({
    "bootstrap uses the first non-empty fetcher result" {
      val resourceFetcher = mockk<ResourceEnemyFetcher>()
      val databaseFetcher = mockk<DatabaseEnemyFetcher>()
      val resourceFetcherInstance = mockk<Instance<ResourceEnemyFetcher>>()
      val catalog = EnemyTemplateCatalog()
      val expectedEnemies = listOf(trainingDummyEnemy(), strawGolemEnemy())

      every { resourceFetcherInstance.isResolvable } returns true
      every { resourceFetcherInstance.get() } returns resourceFetcher
      every { resourceFetcher.fetch() } returns emptyList()
      every { databaseFetcher.fetch() } returns expectedEnemies

      EnemyTemplateBootstrap(databaseFetcher, resourceFetcherInstance, catalog).load()

      catalog.all() shouldContainExactly expectedEnemies.sortedBy { it.name }
    }
  })
