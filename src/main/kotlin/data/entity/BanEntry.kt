package tech.trip_kun.sinon.data.entity

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable

@DatabaseTable(tableName = "bans")
class BanEntry {
    @DatabaseField(generatedId = true)
    var id: Int = 0
    @DatabaseField(canBeNull = false)
    var userId: Long = 0
    @DatabaseField(canBeNull = false)
    var guildId: Long = 0
    @DatabaseField(canBeNull = false)
    var reason: String = ""
    @DatabaseField(canBeNull = false)
    var time: Long = 0
    @DatabaseField(canBeNull = false)
    var bannedBy: Long = 0
}