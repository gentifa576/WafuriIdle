package com.wafuri.idle.tests.application

import com.wafuri.idle.application.service.character.CharacterTemplateBootstrap
import com.wafuri.idle.application.service.character.CharacterTemplateCatalog
import com.wafuri.idle.application.service.character.DatabaseCharacterFetcher
import com.wafuri.idle.application.service.character.ResourceCharacterFetcher
import com.wafuri.idle.domain.model.CharacterTemplate
import com.wafuri.idle.domain.model.StatGrowth
import com.wafuri.idle.tests.support.clericTemplate
import com.wafuri.idle.tests.support.warriorTemplate
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import jakarta.enterprise.inject.Instance

class CharacterTemplateBootstrapTest :
  StringSpec({
    "bootstrap uses the first non-empty fetcher result" {
      val resourceFetcher = mockk<ResourceCharacterFetcher>()
      val databaseFetcher = mockk<DatabaseCharacterFetcher>()
      val resourceFetcherInstance = mockk<Instance<ResourceCharacterFetcher>>()
      val catalog = CharacterTemplateCatalog()
      val expectedTemplates =
        listOf(
          CharacterTemplate(
            key = "warrior",
            name = "Warrior",
            strength = StatGrowth(12f, 2f),
            agility = StatGrowth(7f, 1.1f),
            intelligence = StatGrowth(4f, 0.4f),
            wisdom = StatGrowth(5f, 0.5f),
            vitality = StatGrowth(11f, 1.8f),
            skill = warriorTemplate().skill,
          ),
          CharacterTemplate(
            key = "cleric",
            name = "Cleric",
            strength = StatGrowth(5f, 0.6f),
            agility = StatGrowth(6f, 0.7f),
            intelligence = StatGrowth(9f, 1.4f),
            wisdom = StatGrowth(12f, 1.9f),
            vitality = StatGrowth(8f, 1.2f),
            passive = clericTemplate().passive,
          ),
        )

      every { resourceFetcherInstance.isResolvable } returns true
      every { resourceFetcherInstance.get() } returns resourceFetcher
      every { resourceFetcher.fetch() } returns emptyList()
      every { databaseFetcher.fetch() } returns expectedTemplates

      CharacterTemplateBootstrap(databaseFetcher, resourceFetcherInstance, catalog).load()

      catalog.all() shouldContainExactly expectedTemplates.sortedBy { it.name }
    }
  })
