# Keep JavaMail / AndroidMail / Activation providers and reflection-loaded MIME handlers
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-keep class com.sun.activation.** { *; }
-keep class org.apache.harmony.** { *; }

-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**
-dontwarn com.sun.activation.**
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn org.apache.harmony.**
