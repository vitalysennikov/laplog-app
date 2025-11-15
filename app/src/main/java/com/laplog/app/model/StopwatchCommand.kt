package com.laplog.app.model

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Commands that can be sent from the notification service to the ViewModel
 */
sealed class StopwatchCommand {
    object Start : StopwatchCommand()
    object Pause : StopwatchCommand()
    object Resume : StopwatchCommand()
    object Stop : StopwatchCommand()
    object Lap : StopwatchCommand()
    object LapAndPause : StopwatchCommand()
}

/**
 * Singleton that manages communication between StopwatchService and StopwatchViewModel.
 * Service sends commands, ViewModel executes them.
 */
object StopwatchCommandManager {
    private val _commands = MutableSharedFlow<StopwatchCommand>(replay = 0)
    val commands: SharedFlow<StopwatchCommand> = _commands.asSharedFlow()

    suspend fun sendCommand(command: StopwatchCommand) {
        _commands.emit(command)
    }
}
