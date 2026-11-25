# Android redesign

The [Android design system and mockups](android-design-system.html) are the visual baseline for the new Fitty Android app. Open the HTML file in a browser to inspect the design system and the 20 screen and interaction mockups. The original file was supplied as “Fitty — дизайн-система и макеты Android.html”.

Use the mockups for screen layouts, typography, colors, components, and the interactions they illustrate. Resolve competing variants and unspecified behavior during design review. Review the gym workflow first, from choosing a training day to opening the completed workout in History.

The owner accepts losing existing local data when moving the current personal installation to the redesigned app. This exception applies to that transition only. Clearing data is optional.

## Gym workflow

- After the lifter finishes a training day, Home suggests the next day in program order and wraps to the first day after the last. The lifter can select another day and review its exercises before tapping Start.
- Reopening Fitty during a workout returns the lifter to the same exercise with the logged sets and any remaining rest time.
- Entering a complete result in an empty set row saves the set. The lifter can correct it and add extra rows beyond the planned sets.
- For the first set of an exercise, suggest the first logged set from the most recent completed workout that included a result for that exercise. After the lifter logs a set, suggest its actual values in the next row only.
- Clearly label values supplied by Fitty as suggestions and identify their source. The lifter can accept a suggestion in one tap or edit the values and then tap «Записать». Only confirmation adds a suggested row to workout history.
