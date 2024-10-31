package tech.trip_kun.sinon.data.entity

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable

@DatabaseTable(tableName = "users")
class User {
    constructor(id: Long) {
        this.id = id
    }
    private constructor()
    @DatabaseField(id=true)
    var id: Long = 0
    @DatabaseField(canBeNull = false)
    var allowCurrencyNotifications: Boolean = false
}