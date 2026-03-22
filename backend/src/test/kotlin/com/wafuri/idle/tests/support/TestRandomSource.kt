package com.wafuri.idle.tests.support

import com.wafuri.idle.application.service.combat.RandomSource
import io.quarkus.test.Mock
import jakarta.enterprise.context.ApplicationScoped

@Mock
@ApplicationScoped
class TestRandomSource : RandomSource {
  override fun nextFloat(): Float = 0f

  override fun nextInt(until: Int): Int = 0
}
