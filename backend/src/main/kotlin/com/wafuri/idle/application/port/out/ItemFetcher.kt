package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.Item

interface ItemFetcher {
  fun fetch(): List<Item>
}
