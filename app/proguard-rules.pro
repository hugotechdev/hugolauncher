# Services started by Android must keep their names.
-keep class tz.co.hugotech.launcher.MusicListenerService { *; }
-keep class tz.co.hugotech.launcher.LockService { *; }
-keep class tz.co.hugotech.launcher.ReminderReceiver { *; }
-keep class tz.co.hugotech.launcher.WatchWorker { *; }
# Encrypted key storage (Tink) references annotations that are not shipped.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
