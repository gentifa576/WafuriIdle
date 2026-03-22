package com.wafuri.idle.application.port.out

import com.wafuri.idle.domain.model.ZoneTemplate

interface ZoneFetcher {
  fun fetch(): List<ZoneTemplate>
}
