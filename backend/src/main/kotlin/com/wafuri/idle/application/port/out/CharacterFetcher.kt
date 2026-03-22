package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.CharacterTemplate

interface CharacterFetcher {
  fun fetch(): List<CharacterTemplate>
}
