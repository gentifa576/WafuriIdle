package com.wafuri.idle.application.service.enemy

import com.wafuri.idle.application.port.out.EnemyFetcher
import com.wafuri.idle.application.port.out.EnemyTemplateRepository
import com.wafuri.idle.domain.model.EnemyTemplate
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class DatabaseEnemyFetcher(
  private val enemyTemplateRepository: EnemyTemplateRepository,
) : EnemyFetcher {
  override fun fetch(): List<EnemyTemplate> = enemyTemplateRepository.findAll()
}
