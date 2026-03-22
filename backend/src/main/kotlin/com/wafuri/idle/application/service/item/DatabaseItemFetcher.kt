package com.wafuri.idle.application.service.item

import com.wafuri.idle.application.port.out.ItemFetcher
import com.wafuri.idle.application.port.out.ItemTemplateRepository
import com.wafuri.idle.domain.model.Item
import jakarta.enterprise.context.ApplicationScoped
import jakarta.transaction.Transactional

@ApplicationScoped
class DatabaseItemFetcher(
  private val itemTemplateRepository: ItemTemplateRepository,
) : ItemFetcher {
  @Transactional
  override fun fetch(): List<Item> = itemTemplateRepository.findAll()
}
