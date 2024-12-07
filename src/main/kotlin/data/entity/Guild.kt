package tech.trip_kun.sinon.data.entity

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.field.ForeignCollectionField
import com.j256.ormlite.table.DatabaseTable
import tech.trip_kun.sinon.annotation.ReflectionNoArg

@ReflectionNoArg
@DatabaseTable(tableName = "guilds")
class Guild(@DatabaseField(id = true) var id: Long) {
    @DatabaseField(canBeNull = false)
    var currencyEnabled: Boolean = false

    @DatabaseField(canBeNull = false)
    var starboardChannelId: Long = 0

    @DatabaseField(canBeNull = false)
    var starboardLimit: Int = 3 // Default to 3 stars needed to go to starboard

    @ForeignCollectionField(eager = true)
    var starboardEntries: Collection<StarboardEntry> = listOf()
}


@ReflectionNoArg
@DatabaseTable(tableName = "starboard_entries")
class StarboardEntry(
    @DatabaseField(foreign = true, canBeNull=false) var guild: Guild,
    @DatabaseField(id = true) val messageId: Long, // The original message id
    @DatabaseField(canBeNull = false) val channelId: Long // The original channel id
) {

    @DatabaseField(canBeNull = false) // This is not in the constructor because it is set after the message gets sent
    var starboardMessageId: Long = 0 // The corresponding message in the starboard

}