package com.github.shadowsocks.database

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable

@DatabaseTable(tableName = "appstate")
class AppState {
  @DatabaseField(generatedId = true)
  var id: Int = 0

  @DatabaseField
  var profile_id: Int = -1

  @DatabaseField
  var per_app_proxy_enable: Boolean = false

  @DatabaseField
  var bypass_mode: Boolean = false

  @DatabaseField
  var package_names: String = ""

  @DatabaseField
  var dns_nocache: String = "off"
}
