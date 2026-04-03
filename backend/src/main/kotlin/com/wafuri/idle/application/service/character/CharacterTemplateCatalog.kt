package com.wafuri.idle.application.service.character

import com.wafuri.idle.application.exception.ResourceNotFoundException
import com.wafuri.idle.domain.model.CharacterTemplate
import jakarta.enterprise.context.ApplicationScoped
import java.util.concurrent.atomic.AtomicReference

@ApplicationScoped
class CharacterTemplateCatalog {
  private val templates = AtomicReference<Map<String, CharacterTemplate>>(emptyMap())

  fun replace(newTemplates: Set<CharacterTemplate>) {
    templates.set(newTemplates.associateBy { it.key })
  }

  fun all(): List<CharacterTemplate> = templates.get().values.sortedBy { it.name }

  fun find(characterKey: String): CharacterTemplate? = templates.get()[characterKey]

  fun require(characterKey: String): CharacterTemplate =
    find(characterKey)
      ?: throw ResourceNotFoundException("Character template $characterKey was not found.")

  fun requireAll(characterKeys: List<String>): List<CharacterTemplate> = characterKeys.map(::require)
}
