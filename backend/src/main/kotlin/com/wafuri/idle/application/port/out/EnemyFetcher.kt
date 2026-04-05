package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.EnemyTemplate

interface EnemyFetcher {
  fun fetch(): List<EnemyTemplate>
}
