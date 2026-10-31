# TV2 Admin management — integrated

The TV2 branch codex/member2-admin-management is merged into main. Its public AdminDashboardFragment class and shared DatabaseHelper APIs are preserved.

## Navigation

Admin login opens StudentListActivity. The Môn học button opens SubjectListActivity, which hosts AdminDashboardFragment. Add/edit subjects uses SubjectFormActivity. Student CRUD remains on TV1's existing StudentListActivity/StudentFormActivity to avoid two diverging implementations. The fragment's Sinh viên button returns to that screen. Duyệt lịch opens ScheduleApprovalActivity.

All three activities are registered as non-exported and protected by AdminActivity. SQLite reads and writes run on workers. Accepted writes and their results are retained with OperationViewModel across rotation. Subject codes, required fields and non-negative integer credits are validated; referenced subjects cannot be deleted.

ScheduleApprovalRepository verifies the Admin account in SQLite. Confirming a past or conflicting booking is rejected; Student cannot use this repository to approve. Student schedule edits are submitted as Pending, and only Admin confirms them.

## Tests

AdminCrudTest now uses a disposable database, not the installed app database. CRUD calls are tested in their own transactions rather than inside an extra outer transaction: Android nested transaction failure only rolls back the outer transaction when it ends, so inspecting it before the outer end gave a misleading failed assertion.

IntegratedUiFlowTest exercises real screens with unique disposable fixture rows: login Admin → create/edit subject → approve booking → login Student → edit Confirmed to Pending → cancel → logout. It deletes only its own rows and restores the previous stored login. OperationViewModelTest checks retention and exactly one accepted task through a recreated ViewModelProvider/store.

Build/test command:
`gradlew.bat assembleDebug testDebugUnitTest connectedDebugAndroidTest lintDebug --no-configuration-cache --offline --console=plain`

No schema migration or Gradle/dependency change is required. AGP stays 9.3.0.
