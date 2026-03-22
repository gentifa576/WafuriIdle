package com.wafuri.idle.application.service.character

import com.wafuri.idle.application.port.out.CharacterFetcher
import com.wafuri.idle.application.port.out.CharacterTemplateRepository
import com.wafuri.idle.domain.model.CharacterTemplate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
class DatabaseCharacterFetcher(
  private val characterTemplateRepository: CharacterTemplateRepository,
) : CharacterFetcher {
  @Transactional
  override fun fetch(): List<CharacterTemplate> = characterTemplateRepository.findAll()
}
