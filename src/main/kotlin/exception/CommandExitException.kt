package tech.trip_kun.sinon.exception
/**
 * Exception thrown when the command should exit
 * This exception should be caught and the message should be displayed to the user
 * The program will continue as usual. This exception is expected to be thrown.
 * The intended use is when a command is unable to continue due to user error, it will bubble up to display the error message
 * @param message The message to display
 * @constructor Create a new CommandExitException
 */
class CommandExitException(message: String) : RuntimeException(message)