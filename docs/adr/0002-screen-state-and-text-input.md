# Keep screen state in StateFlow and the text cursor in Compose

Each ViewModel exposes one immutable `UiState` through `StateFlow`. Starting a detail view or opening the workout replacement chooser selects the ID to observe. Closing it cancels the query and clears its result.

For drafts owned by a ViewModel, Compose updates its local `TextFieldValue` as soon as the keyboard reports an edit. The ViewModel synchronously records the raw text for that field, so a save immediately after typing reads the latest draft. Compose initializes the text buffer again when the draft identity changes or the editor explicitly resets it. This keeps cursor and selection stable while the screen receives state updates. The history set editor keeps its draft in the sheet.
