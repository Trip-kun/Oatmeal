package tech.trip_kun.sinon.data.entity

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import tech.trip_kun.sinon.annotation.ReflectionNoArg

@ReflectionNoArg
@DatabaseTable(tableName = "reminders")
class Reminder(@DatabaseField(foreign = true, canBeNull = false) var user: User) {
    @DatabaseField(generatedId = true)
    var id: Int = 0

    @DatabaseField(canBeNull = false)
    var reminder: String = ""
    @DatabaseField(canBeNull = false)
    var timeUnix: Long = 0
}