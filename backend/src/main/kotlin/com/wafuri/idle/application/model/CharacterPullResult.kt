package com.wafuri.idle.application.model

import com.wafuri.idle.domain.model.Player

data class CharacterPull(
  val pulledCharacterKey: String,
  val grantedCharacterKey: String?,
  val essenceGranted: Int,
)

data class CharacterPullResult(
  val player: Player,
  val count: Int,
  val pulls: List<CharacterPull>,
  val totalEssenceGranted: Int,
)
