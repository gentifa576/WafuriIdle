package com.wafuri.idle.application.service.zone

import com.wafuri.idle.application.port.out.ZoneFetcher
import com.wafuri.idle.application.port.out.ZoneTemplateRepository
import com.wafuri.idle.domain.model.ZoneTemplate
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
class DatabaseZoneFetcher(
  private val zoneTemplateRepository: ZoneTemplateRepository,
) : ZoneFetcher {
  @Transactional
  override fun fetch(): List<ZoneTemplate> = zoneTemplateRepository.findAll()
}
