package com.modularhub.app

import android.app.Application

class ModularHubApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 壳级全局初始化：路由注册、监控、账号 SDK 等
    }
}
