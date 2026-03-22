package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.CharacterTemplate

interface CharacterTemplateRepository : Repository<CharacterTemplate, String> {
  fun findAll(): List<CharacterTemplate>
}
