package com.wafuri.idle.application.service.character

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.wafuri.idle.application.port.out.CharacterFetcher
import com.wafuri.idle.domain.model.CharacterTemplate
import com.wafuri.idle.domain.model.StatGrowth
import io.quarkus.arc.profile.UnlessBuildProfile
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
@UnlessBuildProfile("prod")
class ResourceCharacterFetcher(
  private val objectMapper: ObjectMapper,
) : CharacterFetcher {
  override fun fetch(): List<CharacterTemplate> {
    val stream = javaClass.classLoader.getResourceAsStream(RESOURCE_PATH) ?: return emptyList()
    return stream.use { input ->
      objectMapper
        .readValue(input, object : TypeReference<List<ResourceCharacterTemplateRecord>>() {})
        .map {
          CharacterTemplate(
            key = it.id,
            name = it.name,
            strength = it.strength.toStatGrowth("strength", it.id),
            agility = it.agility.toStatGrowth("agility", it.id),
            intelligence = it.intelligence.toStatGrowth("intelligence", it.id),
            wisdom = it.wisdom.toStatGrowth("wisdom", it.id),
            vitality = it.vitality.toStatGrowth("vitality", it.id),
            image = it.image,
            skillRefs = emptyList(),
            passiveRef = null,
          )
        }
    }
  }

  companion object {
    private const val RESOURCE_PATH = "characters/characters.json"
  }
}

private data class ResourceCharacterTemplateRecord(
  val id: String,
  val name: String,
  val strength: List<Float>,
  val agility: List<Float>,
  val intelligence: List<Float>,
  val wisdom: List<Float>,
  val vitality: List<Float>,
  val image: String? = null,
)

private fun List<Float>.toStatGrowth(
  statName: String,
  characterId: String,
): StatGrowth {
  require(size == 2) {
    "Character template $characterId must define $statName as [base, increment]."
  }
  return StatGrowth(base = first(), increment = last())
}
