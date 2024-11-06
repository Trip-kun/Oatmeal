package tech.trip_kun.sinon.data.entity

import com.j256.ormlite.field.DataType
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.field.ForeignCollectionField
import com.j256.ormlite.table.DatabaseTable
import tech.trip_kun.sinon.annotation.ReflectionNoArg
import java.util.TimeZone

@ReflectionNoArg
@DatabaseTable(tableName = "users")
class User(@DatabaseField(id = true) var id: Long) {

    @DatabaseField(canBeNull = false)
    var allowCurrencyNotifications: Boolean = false
    @DatabaseField(canBeNull = false)
    var timeZone: String = TimeZone.getTimeZone("America/Detroit").toZoneId().id
    @ForeignCollectionField(eager = true)
    var reminders: Collection<Reminder> = listOf()
}