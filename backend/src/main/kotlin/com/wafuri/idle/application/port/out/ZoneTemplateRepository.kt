package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.ZoneTemplate

interface ZoneTemplateRepository : Repository<ZoneTemplate, String> {
  fun findAll(): List<ZoneTemplate>
}
