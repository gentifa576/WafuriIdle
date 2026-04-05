package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.EnemyTemplate

interface EnemyTemplateRepository : Repository<EnemyTemplate, String> {
  fun findAll(): List<EnemyTemplate>
}
