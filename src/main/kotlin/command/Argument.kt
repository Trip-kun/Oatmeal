package tech.trip_kun.sinon.command

import net.dv8tion.jda.api.entities.Message.Attachment
import net.dv8tion.jda.api.entities.User

enum class ArgumentType {
    COMMAND,
    INTEGER,
    UINT,
    UINT_OVER_ZERO,
    WORD,
    TEXT,
    USER,
    CHANNEL,
    ROLE,
    BOOLEAN,
    DECIMAL,
    UDECIMAL,
    UDECIMAL_OVER_ZERO,
    ATTACHMENT,
    SUBCOMMAND,
}
class Argument(
    private val name: String,
    private val description: String,
    private val required: Boolean,
    private val type: ArgumentType,
    private val choices: ArrayList<String>?
) {
    init {
        if (type==ArgumentType.SUBCOMMAND) {
            if (choices == null) {
                throw IllegalArgumentException("Subcommand argument must have choices")
            }
        }
    }
    fun getName(): String {
        return this.name;
    }
    fun getDescription(): String {
        return this.description;
    }
    fun getRequired(): Boolean {
        return this.required;
    }
    fun getType(): ArgumentType {
        return this.type;
    }
    fun getChoices(): List<String>? {
        return this.choices;
    }
}
class ParsedArgument {
    private var argumentType: ArgumentType? = null
    private var stringValue: String? = null
    private var intValue: Int? = null
    private var decimalValue: Double? = null
    private var longValue: Long? = null
    private var attachmentValue: Attachment? = null
    private var booleanValue: Boolean? = null
    constructor(argumentType: ArgumentType, value: String) {
        this.argumentType = argumentType
        this.stringValue = value
    }
    constructor(argumentType: ArgumentType, value: Int) {
        this.argumentType = argumentType
        this.intValue = value
    }
    constructor(argumentType: ArgumentType, value: Double) {
        this.argumentType = argumentType
        this.decimalValue = value
    }
    constructor(argumentType: ArgumentType, value: Long) {
        this.argumentType = argumentType
        this.longValue = value
    }
    constructor(argumentType: ArgumentType, value: Attachment) {
        this.argumentType = argumentType
        this.attachmentValue = value
    }
    constructor(argumentType: ArgumentType, value: Boolean) {
        this.argumentType = argumentType
        this.booleanValue = value
    }
    fun getArgumentType(): ArgumentType? {
        return this.argumentType
    }
    fun getStringValue(): String? {
        return this.stringValue
    }
    fun getIntValue(): Int? {
        return this.intValue
    }
    fun getDecimalValue(): Double? {
        return this.decimalValue
    }
    fun getLongValue(): Long? {
        return this.longValue
    }
    fun getAttachmentValue(): Attachment? {
        return this.attachmentValue
    }
    fun getBooleanValue(): Boolean? {
        return this.booleanValue
    }
}