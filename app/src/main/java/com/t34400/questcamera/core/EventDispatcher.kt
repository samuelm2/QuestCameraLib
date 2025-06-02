package com.t34400.questcamera.core

class EventDispatcher<T> {
    private val listeners = mutableListOf<(T) -> Unit>()
    private val lock = Any()

    fun addListener(listener: (T) -> Unit) {
        synchronized(lock) {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: (T) -> Unit) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    fun dispatch(result: T) {
        val snapshot: List<(T) -> Unit>
        synchronized(lock) {
            snapshot = listeners.toList()
            listeners.clear()
        }
        snapshot.forEach { it(result) }
    }
}