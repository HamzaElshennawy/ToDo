package com.hamza.todo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.ui.components.EmptyState
import com.hamza.todo.ui.screens.AddTaskHost
import com.hamza.todo.ui.screens.AddTaskRequest
import com.hamza.todo.ui.screens.ListDetailScreen
import com.hamza.todo.ui.screens.ListsScreen
import com.hamza.todo.ui.screens.SearchScreen
import com.hamza.todo.ui.screens.SettingsScreen
import com.hamza.todo.ui.screens.TaskDetailScreen
import com.hamza.todo.ui.screens.TodayScreen
import com.hamza.todo.ui.screens.UpcomingScreen
import com.hamza.todo.ui.theme.Todo

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Outlined.WbSunny),
    UPCOMING("upcoming", "Upcoming", Icons.Outlined.CalendarMonth),
    LISTS("lists", "Lists", Icons.AutoMirrored.Filled.List),
}

/**
 * Phones (< 600dp wide): bottom navigation bar, one screen at a time.
 * Tablets: navigation rail; Today shows task details beside the list (>= 840dp),
 * Lists shows the chosen list beside the lists, Upcoming becomes a week board.
 */
@Composable
fun TodoApp(vm: MainViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var addRequest by remember { mutableStateOf<AddTaskRequest?>(null) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.messages.collect { msg ->
            val result = snackbar.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = if (msg.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val tablet = maxWidth >= 600.dp
        val wide = maxWidth >= 840.dp
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route ?: Tab.TODAY.route
        val currentTab = Tab.entries.firstOrNull { route.startsWith(it.route) }
            ?: if (route.startsWith("list/")) Tab.LISTS else null
        val onTab = currentTab != null && !route.startsWith("list/")

        fun go(tab: Tab) = nav.navigate(tab.route) {
            popUpTo(Tab.TODAY.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!tablet && onTab) {
                    NavigationBar(containerColor = Todo.colors.card) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = { go(tab) },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!tablet && onTab) {
                    ExtendedFloatingActionButton(
                        onClick = { addRequest = AddTaskRequest() },
                        // The extended FAB hides its text from accessibility; the icon carries the label.
                        icon = { Icon(Icons.Filled.Add, "New task") },
                        text = { Text("New task") },
                    )
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding)) {
                if (tablet) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        header = {
                            FloatingActionButton(onClick = { addRequest = AddTaskRequest() }, modifier = Modifier.padding(vertical = 16.dp)) {
                                Icon(Icons.Filled.Add, "New task")
                            }
                        },
                    ) {
                        Tab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { go(tab) },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        NavigationRailItem(
                            selected = route == "settings",
                            onClick = { nav.navigate("settings") { launchSingleTop = true } },
                            icon = { Icon(Icons.Outlined.Settings, "Settings") },
                            label = null,
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AppNavHost(vm, nav, tablet, wide) { addRequest = it }
                }
            }
        }

        addRequest?.let { req ->
            AddTaskHost(
                request = req,
                asDialog = tablet,
                lists = lists,
                defaultListId = settings.defaultListId,
                now = now,
                parser = remember(lists) { vm.parser() },
                onDismiss = { addRequest = null },
                onAdd = { draft, subs -> vm.addTask(draft, subs) },
            )
        }
    }
}

@Composable
private fun AppNavHost(
    vm: MainViewModel,
    nav: NavHostController,
    tablet: Boolean,
    wide: Boolean,
    onAdd: (AddTaskRequest) -> Unit,
) {
    val openTask: (String) -> Unit = { id -> nav.navigate("task/$id") }

    NavHost(nav, startDestination = Tab.TODAY.route) {
        composable(Tab.TODAY.route) {
            if (wide) {
                var selected by rememberSaveable { mutableStateOf<String?>(null) }
                TwoPane(
                    left = { TodayScreen(vm, onOpenTask = { selected = it }, onSearch = { nav.navigate("search") }, selectedTaskId = selected) },
                    right = {
                        val id = selected
                        if (id == null) EmptyState("No task selected", "Pick a task to see its details here.")
                        else TaskDetailScreen(vm, id, onBack = null)
                    },
                )
            } else {
                TodayScreen(vm, onOpenTask = openTask, onSearch = { nav.navigate("search") })
            }
        }
        composable(Tab.UPCOMING.route) {
            UpcomingScreen(vm, wide = wide, onOpenTask = openTask, onAddOnDay = { onAdd(AddTaskRequest(date = it)) })
        }
        composable(Tab.LISTS.route) {
            if (tablet) {
                var selected by rememberSaveable { mutableStateOf(TaskList.INBOX_ID) }
                TwoPane(
                    leftWidth = if (wide) 360 else 280,
                    left = {
                        ListsScreen(
                            vm,
                            onOpenList = { selected = it },
                            onOpenToday = { nav.navigate(Tab.TODAY.route) },
                            onOpenUpcoming = { nav.navigate(Tab.UPCOMING.route) },
                            onSettings = null,
                            selectedListId = selected,
                            compact = true,
                        )
                    },
                    right = {
                        ListDetailScreen(
                            vm,
                            listId = selected,
                            onBack = { selected = TaskList.INBOX_ID },
                            onOpenTask = openTask,
                            onAddTask = { onAdd(AddTaskRequest(listId = it)) },
                        )
                    },
                )
            } else {
                ListsScreen(
                    vm,
                    onOpenList = { nav.navigate("list/$it") },
                    onOpenToday = { nav.navigate(Tab.TODAY.route) },
                    onOpenUpcoming = { nav.navigate(Tab.UPCOMING.route) },
                    onSettings = { nav.navigate("settings") },
                )
            }
        }
        composable("list/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id") ?: TaskList.INBOX_ID
            ListDetailScreen(vm, id, onBack = { nav.popBackStack() }, onOpenTask = openTask, onAddTask = { onAdd(AddTaskRequest(listId = it)) })
        }
        composable("task/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id") ?: return@composable
            TaskDetailScreen(vm, id, onBack = { nav.popBackStack() })
        }
        composable("search") {
            SearchScreen(vm, onBack = { nav.popBackStack() }, onOpenTask = openTask)
        }
        composable("settings") {
            SettingsScreen(vm, onBack = if (tablet) null else ({ nav.popBackStack() }), wide = wide)
        }
    }
}

@Composable
private fun TwoPane(left: @Composable () -> Unit, right: @Composable () -> Unit, leftWidth: Int = 448) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(leftWidth.dp).fillMaxHeight()) { left() }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Todo.colors.card),
        ) { right() }
    }
}
