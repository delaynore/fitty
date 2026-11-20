# Fitty MVP

Fitty is a personal strength training log. The first version lets the lifter create training programs, follow a program at the gym, and review what they did afterward.

Fitty installs as an app on the lifter's Android phone and opens from its icon. It stores all data on the phone. The full workout workflow, including resuming a workout after closing the app, works in airplane mode.

See the [glossary](../GLOSSARY.md) for training terms.

## Interface

The bottom bar has two tabs, Home and History. From Home, the lifter can create a program or open My Programs. Tapping a program opens its details. Tapping its checkmark selects it for Home, where the lifter chooses a training day and sees its exercises. Each day opens in its own editor.

A workout shows one exercise at a time. The bottom bar has previous and next arrows and an All Exercises button for jumping to any exercise. On the last exercise, Finish replaces the next arrow. The lifter records weight and repetitions in the same row. Fitty shows four set rows when the exercise has no recommended set count.

The prototype shows the flow from the first launch through editing a completed workout in History. Download the HTML file and open it in a browser to try the flow. [Interactive prototype](fitty-mvp-prototype.html)

## Build order

1. **Training programs.** The lifter can create several programs and choose which appears on Home. Each program has training days such as A, B, and C. Each day lists exercises. Recommended sets and repetitions are optional for each exercise.
2. **Workout session.** The lifter selects a training day when starting a workout, then records the weight and repetitions for each set. If they close the app mid-workout, Fitty shows the in-progress workout when they return. They can resume with the logged sets or discard the workout.
3. **Workout history.** Finishing a workout adds it to a separate History tab. The lifter can scroll through completed workouts by date and open one to see its exercises and the weight and repetitions for each logged set.
4. **Rest timer.** During a workout, the lifter taps a button to start a two-minute countdown. Locking the screen leaves the countdown running. On return, Fitty shows the same in-progress workout with its logged sets and any remaining time. The countdown disappears when time runs out or the lifter resets it. Fitty gives no signal while the screen is locked.

The first three steps are enough to try the full logging workflow at the gym. The rest timer completes the MVP.

## Workout records

- Starting a workout creates an in-progress session with a snapshot of the training day: its name, the exercise names in order, and each exercise's recommended sets and repetitions, if provided. Resuming the session uses that snapshot. A new session uses the program as it stands at its own start.
- Finishing marks the session as completed. Cancelling deletes the in-progress session and its logged sets together. The lifter can start a fresh session from the same training day.
- Fitty stores the programs, the program selected for Home, the in-progress workout, and history in a SQLite database in the app's internal storage. It saves the workout at start and writes each set and correction immediately. Finishing moves the workout to History in a single transaction.
- App updates preserve the data, including when the database schema changes. Reinstalling Fitty or changing phones may start with an empty database.
- During a workout, the lifter can correct the weight or repetitions of any logged set or delete an extra set. The lifter can finish with fewer sets than recommended as long as they logged at least one set.
- In History, the lifter can still correct weight and repetitions or delete individual sets. To remove the last set, they delete the workout.
- History keeps exercise names from the time of the workout. Renaming or removing an exercise in the program only affects future workouts.
- Workout history records the sets the lifter actually completed. Training day recommendations change only when the lifter edits the program.

## Android implementation and installation

- Build the Android app in Kotlin with Jetpack Compose for the UI and Room for the SQLite database.
- Keep the MVP in one Gradle module, `:app`. Group screens with their ViewModels in `ui/home/`, `ui/programs/`, `ui/workout/`, and `ui/history/`. Keep navigation in `navigation/`.
- Put training program operations in `data/programs/`. Both the workout screen and workout history use the workout repository in `data/workouts/`. Keep Room entities, DAOs, and database migrations in `data/local/`.
- Compose screens send user actions to their `ViewModel` and render the state it exposes. The `ViewModel` calls repositories directly. Repositories access Room through DAOs and enforce rules for changes to stored data. For example, the workout repository checks for a logged set and completes the workout in the same transaction.
- Extract a use case when several screens need the same business logic or when coordinating a complex operation would make a `ViewModel` hard to follow.
- Install the signed APK directly on the phone. To update, install the new APK over the existing app with the same `applicationId` and signing key and a higher `versionCode`. Retain the signing key for future updates.
- When the schema changes, provide a Room migration and verify that an update preserves the programs, the program selected for Home, an in-progress workout, and workout history.
- Calculate the remaining rest time from the manual start using `SystemClock.elapsedRealtime()`. Recalculate on return to the app and use the workout screen to show any remaining time.

[Technology and installation decision](https://github.com/delaynore/fitty/issues/7#issuecomment-7041030623)

## Outside the MVP

The lifter chooses the training day at each start, so missed gym days require no rescheduling. A recurring schedule, a calendar view, and progress charts are outside the MVP.

Manual export, restore, and configurable rest duration are deferred.

See the [MVP specification](https://github.com/delaynore/fitty/issues/8) for the full requirements and implementation tickets.
