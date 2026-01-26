package com.am24.am24

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import android.content.Context

private const val TAG = "MainViewModel"
private const val PREF_LAST_BOTTOM_NAV_ROUTE = "last_bottom_nav_route"
const val DEFAULT_BOTTOM_NAV_ROUTE = "map"
private val bottomNavRoutes = setOf("profile", "home", "map", "dms", "explore")

/**
 * Container for UI state managed by [MainViewModel].
 */
data class MainUiState(
    val omegleInvite: OmegleMatch? = null,
    val unreadNotifications: Int = 0,
    val allowLocationForMatches: Boolean = false,
    val allowLocationPublic: Boolean = false,
    val isPrivateProfile: Boolean = false,
    val selectedCountry: String = "",
    val selectedCity: String = "",
    val showLocationPrefDialog: Boolean = false,
    val showTopBar: Boolean = true,
    val showBottomBar: Boolean = true,
    val shouldForceSubscription: Boolean = false,
    val forceSubscriptionEnabled: Boolean = false,
    val isTrialExpired: Boolean = false,
    val isPlusAccessExpired: Boolean = false,
    val isPremiumAccessExpired: Boolean = false,
    val hasLocationSpoofAccess: Boolean = false,
    val lastBottomNavRoute: String = DEFAULT_BOTTOM_NAV_ROUTE,
    @StringRes val inviteStatusMessage: Int? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences =
        application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var userRef: DatabaseReference? = null
    private var userListener: ValueEventListener? = null

    private var omegleInvitesRef: DatabaseReference? = null
    private var omegleInviteListener: ValueEventListener? = null

    private var omegleChatStatusRef: DatabaseReference? = null
    private var omegleChatStatusListener: ValueEventListener? = null

    private var notificationsRef: DatabaseReference? = null
    private var notificationsListener: ValueEventListener? = null
    private var forceSubscriptionRef: DatabaseReference? = null
    private var forceSubscriptionListener: ValueEventListener? = null
    private var listenersStarted = false
    private var currentRoute: String? = null
    private var currentUserId: String? = null
    private var freeTrialMarked = false

    fun ensureListeners() {
        if (listenersStarted) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        currentUserId = uid
        listenersStarted = true
        startUserListener(uid)
        startOmegleInviteListener(uid)
        startNotificationsListener(uid)
        startForceSubscriptionListener()
    }

    fun onRouteChanged(route: String?) {
        if (currentRoute == route) return
        currentRoute = route
        recomputeGating()
        persistBottomNavRoute(route)
    }

    fun showLocationDialog() {
        updateState { copy(showLocationPrefDialog = true) }
    }

    fun hideLocationDialog() {
        updateState { copy(showLocationPrefDialog = false) }
    }

    fun updateLocationPreferences(
        allowMatches: Boolean,
        allowPublic: Boolean,
        isPrivate: Boolean
    ) {
        val uid = currentUserId ?: return
        val ref = FirebaseRefs.db.getReference("users").child(uid)
        ref.child("allowLocationForMatches").setValue(allowMatches)
        ref.child("allowLocationPublic").setValue(allowPublic)
        ref.child("isPrivate").setValue(isPrivate)
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save preference: ${e.message}")
            }
        updateState {
            copy(
                allowLocationForMatches = allowMatches,
                allowLocationPublic = allowPublic,
                isPrivateProfile = isPrivate,
                showLocationPrefDialog = false
            )
        }
    }

    fun clearInviteStatusMessage() {
        updateState { copy(inviteStatusMessage = null) }
    }

    fun acceptOmegleInvite(match: OmegleMatch) {
        val uid = currentUserId ?: return
        val ref = FirebaseRefs.db.reference
        ref.child("omegleChats").child(match.chatId).child("status").setValue("accepted")
        ref.child("omegleInvites").child(uid).child(match.chatId).removeValue()
        stopOmegleChatStatusListener()
        updateState { copy(omegleInvite = null) }
    }

    fun rejectOmegleInvite(match: OmegleMatch) {
        val uid = currentUserId ?: return
        val ref = FirebaseRefs.db.reference
        ref.child("omegleChats").child(match.chatId).child("status").setValue("rejected")
        ref.child("omegleInvites").child(uid).child(match.chatId).removeValue()
        stopOmegleChatStatusListener()
        updateState { copy(omegleInvite = null) }
    }

    fun markInviteConsumed() {
        stopOmegleChatStatusListener()
        updateState { copy(omegleInvite = null) }
    }

    fun clearLocationSpoofing() {
        val uid = currentUserId ?: return
        val ref = FirebaseRefs.db.getReference("users").child(uid)
        ref.updateChildren(
            mapOf(
                "isLocationSpoofed" to false,
                "country" to "",
                "city" to ""
            )
        )
        updateState { copy(selectedCountry = "", selectedCity = "") }
    }

    fun setSpoofedLocation(country: String, city: String) {
        val uid = currentUserId ?: return
        val ref = FirebaseRefs.db.getReference("users").child(uid)
        ref.updateChildren(
            mapOf(
                "country" to country,
                "city" to city,
                "isLocationSpoofed" to true
            )
        )
        updateState { copy(selectedCountry = country, selectedCity = city) }
    }

    override fun onCleared() {
        super.onCleared()
        stopUserListener()
        stopOmegleInviteListener()
        stopOmegleChatStatusListener()
        stopNotificationsListener()
        stopForceSubscriptionListener()
    }

    private fun startUserListener(uid: String) {
        val ref = FirebaseRefs.db.getReference("users").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                handleUserSnapshot(snapshot)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to fetch profile: ${error.message}")
            }
        }
        userRef = ref
        userListener = listener
        ref.addValueEventListener(listener)
    }

    private fun handleUserSnapshot(snapshot: DataSnapshot) {
        val allowMatches = snapshot.child("allowLocationForMatches").getValue(Boolean::class.java)
        val allowPublic = snapshot.child("allowLocationPublic").getValue(Boolean::class.java)
        val isPrivate = snapshot.child("isPrivate").getValue(Boolean::class.java)
        val resolvedAllowMatches = allowMatches ?: false
        val resolvedAllowPublic = allowPublic ?: false
        val resolvedIsPrivate = isPrivate ?: false

        if (allowMatches == null || allowPublic == null || isPrivate == null) {
            val uid = currentUserId ?: return
            val ref = FirebaseRefs.db.getReference("users").child(uid)
            if (allowMatches == null) ref.child("allowLocationForMatches").setValue(false)
            if (allowPublic == null) ref.child("allowLocationPublic").setValue(false)
            if (isPrivate == null) ref.child("isPrivate").setValue(false)
        }

        val isPlus = snapshot.child("isPlus").getValue(Boolean::class.java) == true
        val isPremium = snapshot.child("isPremium").getValue(Boolean::class.java) == true
        val loginPlusExpiry = snapshot.child("loginPlusExpiry").getValue(Long::class.java) ?: 0L
        val entryFeePaidAt = snapshot.child("entryFeePaidAt").getValue(Long::class.java) ?: 0L
        val isEntryFeePaid = snapshot.child("isEntryFeePaid").getValue(Boolean::class.java) == true
        val premiumExpiryDate = snapshot.child("premiumExpiryDate").getValue(Long::class.java)
        val subscriptionStatus = snapshot.child("subscriptionStatus").getValue(String::class.java)
        val nextRenewal = snapshot.child("nextRenewal").getValue(Long::class.java)
        val freeTrialExpiry = snapshot.child("freeTrialExpiry").getValue(Long::class.java) ?: 0L
        val hasUsedTrial = snapshot.child("hasUsedFreeTrial").getValue(Boolean::class.java) == true

        val now = System.currentTimeMillis()
        val trialExpired = hasUsedTrial && (freeTrialExpiry == 0L || freeTrialExpiry <= now)
        val entryFeeExpiryFromPaidAt = if (entryFeePaidAt > 0L) {
            entryFeePaidAt + TimeUnit.DAYS.toMillis(30)
        } else {
            0L
        }
        val plusExpiryCandidates = listOf(
            nextRenewal ?: 0L,
            loginPlusExpiry,
            entryFeeExpiryFromPaidAt
        )
        val plusExpiry = plusExpiryCandidates.maxOrNull() ?: 0L
        val entryFeeStillActive = plusExpiry > now
        val hadEntryFeePurchase = plusExpiryCandidates.any { it > 0L } || isEntryFeePaid
        val plusAccessExpired = !isPlus && !isPremium && hadEntryFeePurchase && !entryFeeStillActive

        val premiumExpiryValue = premiumExpiryDate ?: 0L
        val subscriptionActive = subscriptionStatus?.equals("active", ignoreCase = true) == true
        val premiumStillActive = (premiumExpiryValue > now && premiumExpiryValue > 0L) ||
                (nextRenewal ?: 0L > now && (nextRenewal ?: 0L) > 0L) || subscriptionActive
        val hadPremiumPlan = premiumExpiryValue > 0L || (nextRenewal ?: 0L) > 0L ||
                !subscriptionStatus.isNullOrBlank()
        val premiumAccessExpired = !isPlus && !isPremium && hadPremiumPlan && !premiumStillActive

        if (trialExpired && !freeTrialMarked) {
            freeTrialMarked = true
            val uid = currentUserId
            if (!uid.isNullOrBlank()) {
                FirebaseRefs.db
                    .getReference("users/$uid/freeTrialCompleted")
                    .setValue(true)
            }
        }

        val isLocationSpoofed = snapshot.child("isLocationSpoofed").getValue(Boolean::class.java) == true
        val country = snapshot.child("country").getValue(String::class.java) ?: ""
        val city = snapshot.child("city").getValue(String::class.java) ?: ""

        updateState {
            copy(
                allowLocationForMatches = resolvedAllowMatches,
                allowLocationPublic = resolvedAllowPublic,
                isPrivateProfile = resolvedIsPrivate,
                hasLocationSpoofAccess = isPlus || isPremium,
                isTrialExpired = trialExpired,
                isPlusAccessExpired = plusAccessExpired,
                isPremiumAccessExpired = premiumAccessExpired,
                selectedCountry = if (isLocationSpoofed) country else "",
                selectedCity = if (isLocationSpoofed) city else ""
            )
        }

        recomputeGating()
    }

    private fun startOmegleInviteListener(uid: String) {
        val ref = FirebaseRefs.db.reference.child("omegleInvites").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val first = snapshot.children.firstOrNull()
                if (first != null) {
                    val chatId = first.key ?: return
                    val otherUid = first.getValue(String::class.java) ?: return
                    val match = OmegleMatch(chatId, otherUid)
                    updateState { copy(omegleInvite = match) }
                    startOmegleChatStatusListener(chatId)
                } else {
                    markInviteConsumed()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to listen for omegle invites: ${error.message}")
            }
        }
        omegleInvitesRef = ref
        omegleInviteListener = listener
        ref.addValueEventListener(listener)
    }

    private fun startOmegleChatStatusListener(chatId: String) {
        if (omegleChatStatusRef?.key == chatId) return
        stopOmegleChatStatusListener()
        val ref = FirebaseRefs.db.reference
            .child("omegleChats")
            .child(chatId)
            .child("status")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val status = snapshot.getValue(String::class.java)
                if (status == "canceled") {
                    markInviteConsumed()
                    updateState { copy(inviteStatusMessage = R.string.request_canceled) }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        omegleChatStatusRef = ref
        omegleChatStatusListener = listener
        ref.addValueEventListener(listener)
    }

    private fun startNotificationsListener(uid: String) {
        val ref = FirebaseRefs.db.getReference("notifications").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.children.count { child ->
                    val notification = child.getValue(Notification::class.java)
                    notification?.isRead == "false"
                }
                updateState { copy(unreadNotifications = count) }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to fetch unread notifications count: ${error.message}")
            }
        }
        notificationsRef = ref
        notificationsListener = listener
        ref.addValueEventListener(listener)
    }

    private fun startForceSubscriptionListener() {
        val ref = FirebaseRefs.db.reference.child("config").child("forceSubscription")
            .child("enabled")
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val enabled = snapshot.getValue(Boolean::class.java) == true
                updateState { copy(forceSubscriptionEnabled = enabled) }
                recomputeGating()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "forceSubscription flag listener cancelled: ${error.message}")
            }
        }
        forceSubscriptionRef = ref
        forceSubscriptionListener = listener
        ref.addValueEventListener(listener)
    }

    private fun recomputeGating() {
        val state = _uiState.value
        val shouldForceSubscription = state.forceSubscriptionEnabled && (
                state.isTrialExpired ||
                        state.isPlusAccessExpired ||
                        state.isPremiumAccessExpired
                )
        val baseAllowsGlobalBars = currentRoute?.startsWith("chat/") == false &&
                currentRoute != "leaderboard"
        val showTopBar = !shouldForceSubscription && baseAllowsGlobalBars
        val showBottomBar = if (shouldForceSubscription) {
            currentRoute == "settings" || currentRoute?.startsWith("subscription") == true
        } else {
            baseAllowsGlobalBars
        }
        updateState {
            copy(
                shouldForceSubscription = shouldForceSubscription,
                showTopBar = showTopBar,
                showBottomBar = showBottomBar
            )
        }
    }

    fun setLastBottomNavRoute(route: String) {
        if (route !in bottomNavRoutes) return
        preferences.edit().putString(PREF_LAST_BOTTOM_NAV_ROUTE, route).apply()
        updateState { copy(lastBottomNavRoute = route) }
    }

    private fun persistBottomNavRoute(route: String?) {
        if (route == null) return
        if (route in bottomNavRoutes) {
            setLastBottomNavRoute(route)
        }
    }
    private fun stopUserListener() {
        val ref = userRef
        val listener = userListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }
        userRef = null
        userListener = null
    }

    private fun stopOmegleInviteListener() {
        val ref = omegleInvitesRef
        val listener = omegleInviteListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }
        omegleInvitesRef = null
        omegleInviteListener = null
    }

    private fun stopOmegleChatStatusListener() {
        val ref = omegleChatStatusRef
        val listener = omegleChatStatusListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }
        omegleChatStatusRef = null
        omegleChatStatusListener = null
    }

    private fun stopNotificationsListener() {
        val ref = notificationsRef
        val listener = notificationsListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }
        notificationsRef = null
        notificationsListener = null
    }

    private fun stopForceSubscriptionListener() {
        val ref = forceSubscriptionRef
        val listener = forceSubscriptionListener
        if (ref != null && listener != null) {
            ref.removeEventListener(listener)
        }
        forceSubscriptionRef = null
        forceSubscriptionListener = null
    }

    private fun updateState(reducer: MainUiState.() -> MainUiState) {
        _uiState.update { current ->
            reducer(current)
        }
    }

    init {
        val savedRoute = preferences.getString(PREF_LAST_BOTTOM_NAV_ROUTE, DEFAULT_BOTTOM_NAV_ROUTE)
            ?: DEFAULT_BOTTOM_NAV_ROUTE
        updateState { copy(lastBottomNavRoute = savedRoute) }
        viewModelScope.launch {
            ensureListeners()
        }
    }
}