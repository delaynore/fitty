# These enum constant names are stored as strings in custom_exercises.
-keepclassmembers,allowoptimization enum com.delaynore.fitty.data.exercises.ExerciseType {
    public static <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers,allowoptimization enum com.delaynore.fitty.data.exercises.Equipment {
    public static <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers,allowoptimization enum com.delaynore.fitty.data.exercises.MuscleGroup {
    public static <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers,allowoptimization enum com.delaynore.fitty.data.exercises.TechniqueDifficulty {
    public static <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Navigation Compose reflects on the generated serializers and route arguments.
-keepclassmembers,allowoptimization @kotlinx.serialization.Serializable class com.delaynore.fitty.navigation.** {
    public static ** INSTANCE;
    public static ** Companion;
    public kotlinx.serialization.KSerializer serializer(...);
    <fields>;
}
-keepclassmembers,allowoptimization class com.delaynore.fitty.navigation.**$Companion {
    public kotlinx.serialization.KSerializer serializer(...);
}
