package com.wafuri.idle.application.service.enemy

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.port.out.EnemyFetcher
import com.wafuri.idle.domain.model.EnemyTemplate
import io.quarkus.arc.profile.UnlessBuildProfile
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@UnlessBuildProfile("prod")
class ResourceEnemyFetcher(
  private val objectMapper: ObjectMapper,
) : EnemyFetcher {
  private val typeReference = object : TypeReference<List<ResourceEnemyTemplateRecord>>() {}

  override fun fetch(): List<EnemyTemplate> {
    val stream = javaClass.classLoader.getResourceAsStream(RESOURCE_PATH) ?: return emptyList()
    return stream.use { input ->
      objectMapper
        .readValue(input, typeReference)
        .map { record ->
          EnemyTemplate(
            id = record.id,
            name = record.name,
            image = record.image,
            baseHp = record.baseHp,
            attack = record.attack,
          )
        }
    }
  }

  companion object {
    private const val RESOURCE_PATH = "enemies/enemies.json"
  }
}

private data class ResourceEnemyTemplateRecord(
  val id: String,
  val name: String,
  val image: String? = null,
  val baseHp: Float,
  val attack: Float,
)
