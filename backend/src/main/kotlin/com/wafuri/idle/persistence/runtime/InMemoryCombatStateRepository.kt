package com.wafuri.idle.persistence.runtime

import com.wafuri.idle.application.port.out.CombatStateRepository
import com.wafuri.idle.domain.model.CombatState
import com.wafuri.idle.domain.model.CombatStatus
import jakarta.inject.Singleton
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Singleton
class InMemoryCombatStateRepository : CombatStateRepository {
  private val states = ConcurrentHashMap<UUID, CombatState>()

  override fun save(domain: CombatState): CombatState {
    states[domain.playerId] = domain
    return domain
  }

  override fun findById(id: UUID): CombatState? = states[id]

  override fun findActiveZoneIds(): Set<String> =
    states.values
      .asSequence()
      .filter { it.status != CombatStatus.IDLE }
      .mapNotNull { it.zoneId }
      .toSet()

  override fun findActiveByZoneId(zoneId: String): List<CombatState> =
    states.values
      .asSequence()
      .filter { it.status != CombatStatus.IDLE && it.zoneId == zoneId }
      .toList()
}
