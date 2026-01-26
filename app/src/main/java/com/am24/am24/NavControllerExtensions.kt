package com.am24.am24

import androidx.navigation.NavController
import android.util.Log
/**
 * Safely pops the back stack only when there is a previous destination available.
 * Prevents IllegalStateException/IndexOutOfBoundsException that can occur when the
 * back stack is empty (e.g. after deep links or when the start destination is showing).
 */
fun NavController.safePopBackStack(): Boolean {
    return previousBackStackEntry != null && popBackStack()
}

/**
 * Safely pops up to a specific route only when it exists in the back stack.
 */
fun NavController.safePopBackStack(route: String, inclusive: Boolean): Boolean {
    return try {
        getBackStackEntry(route)
        popBackStack(route, inclusive)
    } catch (ignored: IllegalArgumentException) {
        false
    }
}

/**
 * Safely navigates to a route, logging and returning false if the route is invalid.
 */
fun NavController.safeNavigate(route: String): Boolean {
    return try {
        navigate(route)
        true
    } catch (exception: IllegalArgumentException) {
        Log.w("NavController", "Skipping invalid route: $route", exception)
        false
    }
}