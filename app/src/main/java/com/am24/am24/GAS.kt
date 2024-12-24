object ActiveScreenState {
    var currentScreen: String = ""
        private set

    fun updateActiveScreen(screenName: String) {
        currentScreen = screenName
    }
}
