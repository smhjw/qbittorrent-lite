-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# Gson-backed persisted settings and dashboard caches.
-keep class com.hjw.qbremote.data.ConnectionSettings { *; }
-keep class com.hjw.qbremote.data.ServerProfile { *; }
-keep class com.hjw.qbremote.data.ServerProfilesState { *; }
-keep class com.hjw.qbremote.data.ServerDashboardPreferences { *; }
-keep class com.hjw.qbremote.data.DashboardCacheSnapshot { *; }
-keep class com.hjw.qbremote.data.CachedDashboardServerSnapshot { *; }
-keep class com.hjw.qbremote.data.DailyUploadTrackingSnapshot { *; }
-keep class com.hjw.qbremote.data.DailyCountryUploadTrackingSnapshot { *; }
-keep class com.hjw.qbremote.data.model.CountryPeerSnapshot { *; }

# Transmission and qB DTOs parsed via Gson / Retrofit converters.
-keep class com.hjw.qbremote.data.model.** { *; }
-keep class com.hjw.qbremote.data.TransmissionSessionInfo { *; }
-keep class com.hjw.qbremote.data.TransmissionSessionStats { *; }
-keep class com.hjw.qbremote.data.TransmissionCumulativeStats { *; }
-keep class com.hjw.qbremote.data.TransmissionTorrent { *; }
-keep class com.hjw.qbremote.data.TransmissionTracker { *; }
-keep class com.hjw.qbremote.data.TransmissionTrackerStat { *; }
-keep class com.hjw.qbremote.data.TransmissionFile { *; }
-keep class com.hjw.qbremote.data.TransmissionFileStat { *; }

# Preserve serialized field names used by Gson reflection.
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
