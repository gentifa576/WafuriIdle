package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.Item

interface ItemTemplateRepository : Repository<Item, String> {
  fun findAll(): List<Item>
}
