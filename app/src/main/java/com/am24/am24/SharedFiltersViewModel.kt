import androidx.lifecycle.ViewModel
import com.am24.am24.DatingFilterSettings
import com.am24.am24.FeedFilterSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SharedFiltersViewModel : ViewModel() {
    private val _selectedTab = MutableStateFlow(0) // 0 for Dating, 1 for Feed
    val selectedTab: StateFlow<Int> = _selectedTab

    private val _datingFilters = MutableStateFlow(DatingFilterSettings())
    val datingFilters: StateFlow<DatingFilterSettings> = _datingFilters

    private val _feedFilters = MutableStateFlow(FeedFilterSettings())
    val feedFilters: StateFlow<FeedFilterSettings> = _feedFilters

    fun setSelectedTab(tab: Int) {
        _selectedTab.value = tab
    }

    fun updateDatingFilters(filters: DatingFilterSettings) {
        _datingFilters.value = filters
    }

    fun updateFeedFilters(filters: FeedFilterSettings) {
        _feedFilters.value = filters
    }
}
