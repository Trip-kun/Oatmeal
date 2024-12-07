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
class StarboardEntry(@DatabaseField(foreign = true, canBeNull=false) var guild: Guild) {
    @DatabaseField(generatedId = true)
    var id: Int = 0

    @DatabaseField(canBeNull = false)
    var messageId: Long = 0 // The original message id

    @DatabaseField(canBeNull = false)
    var channelId: Long = 0 // The original channel id

    @DatabaseField(canBeNull = false)
    var starboardMessageId: Long = 0 // The corresponding message in the starboard

}