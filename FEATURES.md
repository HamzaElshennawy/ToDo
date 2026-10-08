# ToDo Mobile App — Feature List

Features are grouped by release phase so the app can ship early and grow.

## Phase 1 — MVP (core)

### Task management
- Create a task with a title and optional notes
- Edit a task
- Delete a task (swipe to delete, with undo)
- Mark a task complete / incomplete (tap the checkbox)
- Reorder tasks with drag and drop

### Organization
- Lists (e.g. "Personal", "Work", "Groceries"): create, rename, delete, pick a color/icon
- Default "Inbox" list for tasks without a list
- Show or hide completed tasks; "Clear completed" action

### Dates & priority
- Optional due date and time
- Priority levels: none / low / medium / high
- Overdue tasks highlighted

### Smart views
- **Today**: tasks due today plus overdue
- **Upcoming**: tasks grouped by date
- **All**: every task across lists
- **Completed**: completion history

### Storage
- Offline-first: all data stored locally on the device
- Data survives app restarts

### UI / UX
- Light and dark mode (follows the system setting)
- Quick-add button reachable from every screen
- Empty states with helpful hints

## Phase 2 — Productivity

- **Reminders**: local push notifications at a set time
- **Recurring tasks**: daily, weekly, monthly, custom (e.g. every 2nd Tuesday)
- **Subtasks / checklists** inside a task
- **Search** across task titles and notes
- **Sorting & filtering**: by due date, priority, created date, alphabetical
- **Tags / labels**, with filtering by tag
- **Natural-language input**: "Call mom tomorrow at 5pm !high" sets the date and priority
- Haptic feedback and completion animations

## Phase 3 — Sync across my devices

Single-user app: no sign-up, no login, no sharing or collaboration.

- Sync tasks and lists across my Android phones and tablets
  - Uses the Google account already on each device (Drive app-data folder), so there is no in-app account or login screen
  - Turned on once in Settings; works offline and syncs when back online
  - Conflict handling: last edit wins per task, deleted tasks stay deleted
  - "Last synced" status and a "Sync now" button in Settings
  - Option to sync on Wi-Fi only
  - List of my synced devices with when each last synced
- Backup and restore; export to JSON/CSV

## Phase 4 — Extras

- Home-screen widgets (Today view, quick add)
- Calendar view of tasks by day/week/month
- Attachments (photos, files) on tasks
- Location-based reminders ("remind me when I get to the store")
- Productivity stats: tasks completed per day, streaks
- Focus / Pomodoro timer linked to a task
- Themes and custom accent colors
- App lock with biometrics (Face ID / fingerprint)
- Localization (multiple languages, RTL support e.g. Arabic)
- Accessibility: screen-reader labels, dynamic font sizes, good contrast

## Non-functional requirements

- Android only, phones and tablets
- Tablet layout: navigation rail on the side and two panes (task list + task details, lists + list contents), in portrait and landscape
- Fast startup (< 2 s) and smooth 60 fps scrolling with 1,000+ tasks
- No data loss on crash or when offline
- Privacy: no task data leaves the device unless sync is enabled, and then only to my own Google Drive
