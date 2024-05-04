package nz.eloque.justshop.model



interface EmberObserver {
    fun notifyOfChange()
}

interface EmberObservable {
    fun register(observer: EmberObserver)
    fun notifyObservers()
}