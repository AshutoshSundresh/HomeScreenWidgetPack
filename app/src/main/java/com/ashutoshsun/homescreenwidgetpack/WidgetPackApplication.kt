package com.ashutoshsun.homescreenwidgetpack

import android.app.Application
import com.google.android.material.color.DynamicColors

class WidgetPackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Apply dynamic colors to all activities in the app
        // This enables automatic color adaptation based on user's wallpaper
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
