package com.wafuri.idle.application.model

import com.wafuri.idle.domain.model.Player

data class CharacterPullResult(
  val player: Player,
  val pulledCharacterKey: String,
  val grantedCharacterKey: String?,
  val essenceGranted: Int,
)
