import sys

path = '/home/user/MarbleDo/app/src/main/kotlin/com/marbledo/DashboardScreen.kt'
with open(path, 'r') as f:
    content = f.read()

old_enum = """private enum class DashboardTab(val labelRes: Int) {
    TODAY(R.string.dash_tab_today),
    WEEK(R.string.dash_tab_week),
    OCCASIONS(R.string.dash_tab_occasions),
}"""

new_enum = """private enum class DashboardTab(val labelRes: Int) {
    TODAY(R.string.dash_tab_today),
    WEEK(R.string.dash_tab_week),
    OCCASIONS(R.string.dash_tab_occasions),
    FINANCE(R.string.finance_tab_title),
}"""

if old_enum in content:
    content = content.replace(old_enum, new_enum)
else:
    print("Enum not found")
    sys.exit(1)

old_icon_mapping = """            val icon = when (tab) {
                DashboardTab.TODAY -> Icons.Outlined.AccessTime
                DashboardTab.WEEK -> Icons.Outlined.CalendarMonth
                DashboardTab.OCCASIONS -> Icons.Outlined.StarBorder
            }"""

new_icon_mapping = """            val icon = when (tab) {
                DashboardTab.TODAY -> Icons.Outlined.AccessTime
                DashboardTab.WEEK -> Icons.Outlined.CalendarMonth
                DashboardTab.OCCASIONS -> Icons.Outlined.StarBorder
                DashboardTab.FINANCE -> Icons.Outlined.CalendarMonth
            }"""

if old_icon_mapping in content:
    content = content.replace(old_icon_mapping, new_icon_mapping)
else:
    print("Icon mapping not found")
    sys.exit(1)

# Insert FINANCE case before OCCASIONS
old_block = """                DashboardTab.OCCASIONS -> {
                    // Everything occasion-shaped lives here now. The header no longer repeats
                    // today's occasion above a card that lists today's occasions.
                    item {"""

new_block = """                DashboardTab.FINANCE -> {
                    // Finance monitoring section: professional monitoring, debts and checks
                    item {
                        com.marble098.marbledo.FinanceMonitoringCard(
                            debts = emptyList(), // TODO: Fetch from ViewModel
                            checks = emptyList(), // TODO: Fetch from ViewModel
                            onAddDebt = {}, // TODO: Implement
                            onAddCheck = {}, // TODO: Implement
                            onToggleDebtPaid = {}, // TODO: Implement
                            onToggleCheckCleared = {}, // TODO: Implement
                        )
                    }
                    item { Spacer(Modifier.height(MarbleSpacing.md)) }
                }

                DashboardTab.OCCASIONS -> {
                    // Everything occasion-shaped lives here now. The header no longer repeats
                    // today's occasion above a card that lists today's occasions.
                    item {"""

if old_block in content:
    content = content.replace(old_block, new_block)
else:
    print("Block not found")
    sys.exit(1)

with open(path, 'w') as f:
    f.write(content)

print("Success")
