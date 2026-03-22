package com.wafuri.idle.application.service.combat

import jakarta.enterprise.context.ApplicationScoped
import kotlin.random.Random

interface RandomSource {
  fun nextFloat(): Float

  fun nextInt(until: Int): Int
}

@ApplicationScoped
class DefaultRandomSource : RandomSource {
  override fun nextFloat(): Float = Random.nextFloat()

  override fun nextInt(until: Int): Int = Random.nextInt(until)
}
