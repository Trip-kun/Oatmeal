package tech.trip_kun.sinon.data.entity

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import tech.trip_kun.sinon.annotation.ReflectionNoArg

@ReflectionNoArg
@DatabaseTable(tableName = "database_info")
class DatabaseInfo {
    @DatabaseField(generatedId = true)
    var id: Int = 0

    @DatabaseField(canBeNull = false)
    var currentVersion: Int = 0

    @DatabaseField(canBeNull = false)
    var firstVersion = 0
}