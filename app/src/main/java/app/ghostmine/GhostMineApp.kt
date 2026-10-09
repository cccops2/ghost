package app.ghostmine

import android.app.Application
import app.ghostmine.data.Repo
import app.ghostmine.miner.MinerController
import app.ghostmine.notify.Notifier

class GhostMineApp : Application() {
    override fun onCreate() {
        super.onCreate()
        MinerController.init(this)
        Repo.init(this, MinerController.defaultCustom(this))
        Notifier.init(this)
    }
}
