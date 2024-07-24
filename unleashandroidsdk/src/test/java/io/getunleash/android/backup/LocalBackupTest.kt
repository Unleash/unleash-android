package io.getunleash.android.backup

import io.getunleash.android.BaseTest
import io.getunleash.android.data.UnleashContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LocalBackupTest : BaseTest() {
    @Test
    fun `loads a local backup when context matches`() {
        val backupFile = this::class.java.classLoader?.getResource("unleash-state.json")!!.path
        val tmpDir = createTempDirectory().toFile()
        File(backupFile).copyTo(File(tmpDir, LocalBackup.STATE_BACKUP_FILE))

        val backup = LocalBackup(tmpDir)
        assertThat(backup.loadFromDisc(UnleashContext(userId = "123"))).isNotNull()
    }

    @Test
    fun `does not load a local backup with a different context`() {
        val backupFile = this::class.java.classLoader?.getResource("unleash-state.json")!!.path
        val tmpDir = createTempDirectory().toFile()
        File(backupFile).copyTo(File(tmpDir, LocalBackup.STATE_BACKUP_FILE))

        val backup = LocalBackup(tmpDir)
        assertThat(backup.loadFromDisc(UnleashContext(userId = "456"))).isNull()
    }
}