# Glance 1.1.1 brings WorkManager 2.7.1 / Room 2.2.5. Their consumer rules
# preserve these classes, but AGP 9 R8 no longer implicitly keeps no-arg constructors.
# Room.getGeneratedImplementation instantiates WorkDatabase_Impl by reflection.
-keepclassmembers class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}

# WorkManager also reflectively creates its named InputMerger implementations.
-keepclassmembers class * extends androidx.work.InputMerger {
    public <init>();
}
