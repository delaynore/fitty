---
name: compose-ui-testing-patterns
description: Use when writing or reviewing an Android Compose UI test that Fitty's test-layer rules already justify. Covers test mechanics such as semantics, input, and synchronization.
---

# Compose: UI testing patterns

<!-- Fitty changed cross-skill links to point to upstream revision 44522c0030ca852f8f489bbd155311b20c00b12d. -->

## Core principle

Apply [Fitty's test-layer rules](../../../docs/android-development.md#run-checks) first. Use this skill to implement or review the resulting UI test. Mount the smallest UI surface that exercises the behavior under test.

## Procedure

1. State the behavior and test concern the task asks you to prove.
2. Confirm the test layer using Fitty's rules. Inspect any existing test for the same behavior.
3. Keep focused edits within the requested test concern. Do not move test-only
   helpers into production or broaden production APIs unless that production
   boundary is itself under test.
4. Drive controlled state or input, synchronize through Compose when needed,
   and assert an observable semantic or callback result.
5. Finish with no edit when the existing test already uses the narrowest valid
   seam and proves the requested behavior.

## Prefer plain UI tests

If the screen has a state holder/UI split, test the plain UI composable:

```kotlin
composeTestRule.setContent {
    ProfileScreen(
        state = ProfileUiState(name = "Ada", canSave = true),
        onNameChange = {},
        onSaveClick = { saved = true },
        onBackClick = {},
    )
}

composeTestRule.onNodeWithText("Ada").assertIsDisplayed()
composeTestRule.onNodeWithText("Save").performClick()

assertThat(saved).isTrue()
```

This avoids constructing ViewModels, components, repositories, navigation, and dependency graphs for layout behavior.

## Semantics first

Assert semantics when behavior is semantic:

- Text exists: `onNodeWithText`.
- Button is enabled/disabled: `assertIsEnabled`, `assertIsNotEnabled`.
- Content is selected/focused/toggled: use semantics assertions.
- Content is absent: `assertDoesNotExist`.

Use test tags for nodes that have no stable user-visible text or where multiple nodes share text. Do not use tags as the first choice for all assertions; user-visible semantics are usually stronger.

## Callback testing

Use simple counters or captured values:

```kotlin
var selectedId: String? = null

composeTestRule.setContent {
    ItemList(
        items = listOf(ItemUi("movie-1", "Movie")),
        onItemClick = { selectedId = it },
    )
}

composeTestRule.onNodeWithText("Movie").performClick()

assertThat(selectedId).isEqualTo("movie-1")
```

For plain captured callback values, a direct assertion after the action is usually enough. Use `runOnIdle` when the assertion needs Compose to finish applying snapshot state, recomposition, or queued UI work before reading the result.

## Keep UI tests deterministic

For layout, branch, and callback behavior, render controlled state with `setContent` instead of constructing the production app graph. Production DI, repositories, lifecycle observers, and background effects add asynchronous work that is irrelevant to a plain UI contract and can make the test flaky.

Do not use `Thread.sleep` to wait for Compose. Drive the UI to a known state, then use semantic assertions and Compose synchronization (`waitForIdle`, `runOnIdle`, or a bounded `waitUntil` for a real asynchronous condition). If you set `mainClock.autoAdvance = false`, advance the clock (`mainClock.advanceTimeBy`) to the time you intend to observe before asserting, not between checks of one frame; otherwise leave auto-advance on. Reserve full-app integration for behavior that actually depends on navigation, lifecycle, DI, or platform wiring.

## Interaction state with MutableInteractionSource

When interaction state is the concern, read
[the interaction-state procedure](references/interaction-state.md) completely
before writing or reviewing the test. It requires direct, injected interaction
state rather than fragile pointer simulation.

## Keyboard and focus

For keyboard navigation, send key events and assert focused semantics.
If the inspected composable exposes no input surface that can change the state,
name the missing host or parent interaction seam before proposing a key-driven
test; do not assume a selector or control exists in the supplied UI.

Details—focus graph, `FocusRequester`, restoration, key handlers, and test patterns: [`compose-focus-navigation`](https://github.com/chrisbanes/skills/blob/44522c0030ca852f8f489bbd155311b20c00b12d/skills/compose-focus-navigation/SKILL.md).

## Fake images and platform services

When image content is irrelevant, fake the loader and assert the requested model if that is the behavior. The exact hook depends on your image library; a project helper might look like this:

```kotlin
val requestedModels = mutableListOf<Any?>()

// Example helper, not a Compose API.
setContentWithFakeImageLoader { request ->
    requestedModels += request.data
    errorPainter()
}
```

When image appearance matters, provide a deterministic local painter/bitmap instead of network data.
